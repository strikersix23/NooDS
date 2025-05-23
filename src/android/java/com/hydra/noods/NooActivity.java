/*
    Copyright 2019-2025 Hydr8gon

    This file is part of NooDS.

    NooDS is free software: you can redistribute it and/or modify it
    under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    NooDS is distributed in the hope that it will be useful, but
    WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
    General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with NooDS. If not, see <https://www.gnu.org/licenses/>.
*/

package com.hydra.noods;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.widget.TextView;

public class NooActivity extends AppCompatActivity {
    private boolean running;
    private Thread coreThread, saveThread, fpsThread;

    private ConstraintLayout layout;
    private GLSurfaceView view;
    private NooRenderer renderer;
    private NooButton buttons[];
    private TextView fpsCounter;

    private SharedPreferences prefs;
    private int fpsLimiterBackup = 0;
    private boolean initButtons = true;
    private boolean showingButtons;
    private boolean showingFps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        layout = new ConstraintLayout(this);
        view = new GLSurfaceView(this);
        renderer = new NooRenderer(this);
        buttons = new NooButton[9];
        fpsCounter = new TextView(this);

        // Load the previous on-screen button state
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        showingButtons = prefs.getBoolean("show_buttons", true);

        // Prepare the GL renderer
        view.setEGLContextClientVersion(2);
        view.setRenderer(renderer);

        // Handle non-button screen touches
        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    // Send the touch coordinates to the core
                    pressScreen((int)event.getX(), (int)event.getY());
                    break;

                case MotionEvent.ACTION_UP:
                    // Send a touch release to the core
                    releaseScreen();
                    break;
                }
                return true;
            }
        });

        // Add the view to the layout
        setContentView(layout);
        layout.addView(view);

        // Create the FPS counter, padded for round corners
        fpsCounter.setTextSize(24);
        fpsCounter.setTextColor(Color.WHITE);
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        fpsCounter.setPadding((int)(metrics.density * 10), (int)(metrics.density * 10), 0, 0);

        // Add the FPS counter to the layout if enabled
        if (showingFps = (SettingsMenu.getShowFpsCounter() != 0))
            layout.addView(fpsCounter);
    }

    @Override
    protected void onPause() {
        pauseCore();
        super.onPause();

        // Restore the FPS limiter
        if (fpsLimiterBackup != 0) {
            SettingsMenu.setFpsLimiter(fpsLimiterBackup);
            fpsLimiterBackup = 0;
        }
    }

    @Override
    protected void onResume() {
        resumeCore();
        super.onResume();

        // Hide the status and navigation bars
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.noo_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.controls_action:
            // Toggle the on-screen button state
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("show_buttons", showingButtons = !showingButtons);
            editor.commit();

            // Show or hide the on-screen buttons
            if (showingButtons)
                for (int i = 0; i < 6; i++)
                    layout.addView(buttons[i]);
            else
                for (int i = 0; i < 6; i++)
                    layout.removeView(buttons[i]);
            return true;

        case R.id.restart_action:
            // Restart the core
            pauseCore();
            restartCore();
            resumeCore();
            return true;

        case R.id.save_state_action:
            // Create a confirmation dialog
            AlertDialog.Builder builder = new AlertDialog.Builder(NooActivity.this);
            builder.setTitle("Save State");
            builder.setNegativeButton("Cancel", null);
            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id) {
                    // Save the state if confirmed
                    pauseCore();
                    saveState();
                    resumeCore();
                }
            });

            // Use the message with extra information if a state file doesn't exist yet
            if (checkState() == 1) // File fail
                builder.setMessage("Saving and loading states is dangerous and can lead to data " +
                    "loss. States are also not guaranteed to be compatible across emulator " +
                    "versions. Please rely on in-game saving to keep your progress, and back up " +
                    ".sav files before using this feature. Do you want to save the current state?");
            else
                builder.setMessage("Do you want to overwrite the saved " +
                    "state with the current state? This can't be undone!");
            builder.create().show();
            return true;

        case R.id.load_state_action:
            // Create a confirmation dialog, or an error if something went wrong
            AlertDialog.Builder builder2 = new AlertDialog.Builder(NooActivity.this);
            builder2.setTitle("Load State");
            switch (checkState()) {
            case 0: // Success
                builder2.setMessage("Do you want to load the saved state " +
                    "and lose the current state? This can't be undone!");
                builder2.setNegativeButton("Cancel", null);
                builder2.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        // Load the state if confirmed
                        pauseCore();
                        loadState();
                        resumeCore();
                    }
                });
                break;

            case 1: // File fail
                builder2.setMessage("The state file doesn't exist or couldn't be opened.");
                builder2.setNegativeButton("OK", null);
                break;

            case 2: // Format fail
                builder2.setMessage("The state file doesn't have a valid format.");
                builder2.setNegativeButton("OK", null);
                break;

            case 3: // Version fail
                builder2.setMessage("The state file isn't compatible with this version of NooDS.");
                builder2.setPositiveButton("OK", null);
                break;
            }
            builder2.create().show();
            return true;

        case R.id.save_type_action:
            final boolean gba = isGbaMode();
            final String[] names = getResources().getStringArray(gba ? R.array.save_entries_gba : R.array.save_entries_nds);
            final int[] values = getResources().getIntArray(gba ? R.array.save_values_gba : R.array.save_values_nds);

            // Create the save type dialog
            AlertDialog.Builder builder3 = new AlertDialog.Builder(this);
            builder3.setTitle("Change Save Type");
            builder3.setItems(names, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, final int which) {
                    // Confirm the change because accidentally resizing a working save file could be bad!
                    AlertDialog.Builder builder4 = new AlertDialog.Builder(NooActivity.this);
                    builder4.setTitle("Changing Save Type");
                    builder4.setMessage("Are you sure? This may result in data loss!");
                    builder4.setNegativeButton("Cancel", null);
                    builder4.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            // Apply the change and restart the core
                            pauseCore();
                            if (gba)
                                resizeGbaSave(values[which]);
                            else
                                resizeNdsSave(values[which]);
                            restartCore();
                            resumeCore();
                        }
                    });
                    builder4.create().show();
                }
            });
            builder3.create().show();
            return true;

        case R.id.settings_action:
            // Open the settings menu
            startActivity(new Intent(this, SettingsMenu.class));
            return true;

        case R.id.browser_action:
            // Go back to the file browser
            startActivity(new Intent(this, FileBrowser.class));
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onOptionsMenuClosed(Menu menu) {
        super.onOptionsMenuClosed(menu);

        // Rehide the status and navigation bars
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Trigger a key press if a mapped key was pressed
        for (int i = 0; i < 12; i++) {
            if (keyCode == BindingsPreference.getKeyBind(i) - 1) {
                NooButton.pressKey(i);
                return true;
            }
        }

        // Handle pressing special hotkeys
        if (keyCode == BindingsPreference.getKeyBind(12) - 1) { // Fast Forward Hold
            // Disable the FPS limiter
            if (SettingsMenu.getFpsLimiter() != 0) {
                fpsLimiterBackup = SettingsMenu.getFpsLimiter();
                SettingsMenu.setFpsLimiter(0);
            }
            return true;
        }
        else if (keyCode == BindingsPreference.getKeyBind(13) - 1) { // Fast Forward Toggle
            // Toggle between disabling and restoring the FPS limiter
            if (SettingsMenu.getFpsLimiter() != 0) {
                fpsLimiterBackup = SettingsMenu.getFpsLimiter();
                SettingsMenu.setFpsLimiter(0);
            }
            else if (fpsLimiterBackup != 0) {
                SettingsMenu.setFpsLimiter(fpsLimiterBackup);
                fpsLimiterBackup = 0;
            }
            return true;
        }
        else if (keyCode == BindingsPreference.getKeyBind(14) - 1) { // Screen Swap Toggle
            // Toggle between favoring the top or bottom screen
            SettingsMenu.setScreenSizing((SettingsMenu.getScreenSizing() == 1) ? 2 : 1);
            renderer.updateLayout(renderer.width, renderer.height);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // Trigger a key release if a mapped key was released
        for (int i = 0; i < 12; i++) {
            if (keyCode == BindingsPreference.getKeyBind(i) - 1) {
                NooButton.releaseKey(i);
                return true;
            }
        }

        // Handle releasing special hotkeys
        if (keyCode == BindingsPreference.getKeyBind(12) - 1) { // Fast Forward Hold
            // Restore the FPS limiter
            if (fpsLimiterBackup != 0) {
                SettingsMenu.setFpsLimiter(fpsLimiterBackup);
                fpsLimiterBackup = 0;
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        // Simulate a menu button press to open the menu
        BaseInputConnection inputConnection = new BaseInputConnection(view, true);
        inputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU));
        inputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU));
    }

    public void updateButtons() {
        // Remove old buttons from the layout if visible
        if (!initButtons && showingButtons)
            for (int i = 0; i < 6; i++)
                layout.removeView(buttons[i]);

        // Set layout parameters based on display and settings
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        float d = metrics.density * (SettingsMenu.getButtonScale() + 10) / 15;
        int w = renderer.width, h = renderer.height;
        int b = Math.min(((w > h) ? w : h) * (SettingsMenu.getButtonSpacing() + 10) / 40, h);

        // Create D-pad and ABXY buttons, placed 2/3rds down the virtual controller
        int x = Math.min(h - b / 3 - (int)(d * 82.5), h - (int)(d * 170));
        buttons[0] = new NooButton(this, NooButton.resAbxy, new int[] {0, 11, 10, 1},
            w - (int)(d * 170), x, (int)(d * 165), (int)(d * 165));
        buttons[1] = new NooButton(this, NooButton.resDpad, new int[] {4, 5, 6, 7},
            (int)(d * 21.5), x + (int)(d * 16.5), (int)(d * 132), (int)(d * 132));

        // Create L and R buttons, placed in the top corners of the virtual controller
        int y = Math.min(h - b + (int)(d * 5), x - (int)(d * 49));
        buttons[2] = new NooButton(this, new int[] {R.drawable.l, R.drawable.l_pressed},
            new int[] {9}, (int)(d * 5), y, (int)(d * 110), (int)(d * 44));
        buttons[3] = new NooButton(this, new int[] {R.drawable.r, R.drawable.r_pressed},
            new int[] {8}, w - (int)(d * 115), y, (int)(d * 110), (int)(d * 44));

        // Create start and select buttons, placed along the bottom of the virtual controller
        int z = Math.min(h - y, w / 2) - (int)(d * 49.5);
        buttons[4] = new NooButton(this, new int[] {R.drawable.start, R.drawable.start_pressed},
            new int[] {3}, w - z - (int)(d * 33), h - (int)(d * 38), (int)(d * 33), (int)(d * 33));
        buttons[5] = new NooButton(this, new int[] {R.drawable.select, R.drawable.select_pressed},
            new int[] {2}, z, h - (int)(d * 38), (int)(d * 33), (int)(d * 33));

        // Add new buttons to the layout if visible
        if (showingButtons)
            for (int i = 0; i < 6; i++)
                layout.addView(buttons[i]);
        initButtons = false;
    }

    private boolean canEnableMic() {
        // Check if the microphone is enabled and permission has been granted
        int perm = ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO);
        return perm == PackageManager.PERMISSION_GRANTED && SettingsMenu.getMicEnable() == 1;
    }

    private void pauseCore() {
        // Stop audio output and input if enabled
        running = false;
        if (canEnableMic())
            stopAudioRecorder();
        stopAudioPlayer();

        // Wait for the emulator to stop
        try {
            coreThread.join();
            saveThread.interrupt();
            saveThread.join();
            if (SettingsMenu.getShowFpsCounter() != 0) {
                fpsThread.interrupt();
                fpsThread.join();
            }
        }
        catch (Exception e) {
            // Oh well, I guess
        }

        view.onPause();
    }

    private void resumeCore() {
        // Start audio output and input if enabled
        running = true;
        startAudioPlayer();
        if (canEnableMic())
            startAudioRecorder();

        // Prepare the core thread
        coreThread = new Thread() {
            @Override
            public void run() {
                while (running)
                    runCore();
            }
        };

        coreThread.setPriority(Thread.MAX_PRIORITY);
        coreThread.start();

        // Prepare the save thread
        saveThread = new Thread() {
            @Override
            public void run() {
                while (running) {
                    // Check save files every few seconds and update them if changed
                    try {
                        Thread.sleep(3000);
                    }
                    catch (Exception e) {
                        // Oh well, I guess
                    }

                    writeSave();
                }
            }
        };

        saveThread.setPriority(Thread.MIN_PRIORITY);
        saveThread.start();

        if (SettingsMenu.getShowFpsCounter() != 0) {
            // Add the FPS counter to the layout if enabled
            if (!showingFps) {
                layout.addView(fpsCounter);
                showingFps = true;
            }

            // Prepare the FPS counter thread if enabled
            fpsThread = new Thread() {
                @Override
                public void run() {
                    while (running) {
                        // Update the FPS counter text on the UI thread
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                fpsCounter.setText(Integer.toString(getFps()) + " FPS");
                            }
                        });

                        // Wait a second before updating the FPS counter again
                        try {
                            Thread.sleep(1000);
                        }
                        catch (Exception e) {
                            // Oh well, I guess
                        }
                    }
                }
            };

            fpsThread.setPriority(Thread.MIN_PRIORITY);
            fpsThread.start();
        }
        else if (showingFps) {
            // Remove the FPS counter from the layout if disabled
            layout.removeView(fpsCounter);
            showingFps = false;
        }

        // Resume rendering
        view.onResume();
    }

    public boolean isRunning() { return running; }

    public static native void startAudioPlayer();
    public static native void startAudioRecorder();
    public static native void stopAudioPlayer();
    public static native void stopAudioRecorder();
    public static native int getFps();
    public static native boolean isGbaMode();
    public static native void runCore();
    public static native void writeSave();
    public static native void restartCore();
    public static native int checkState();
    public static native boolean saveState();
    public static native boolean loadState();
    public static native void pressScreen(int x, int y);
    public static native void releaseScreen();
    public static native void resizeGbaSave(int size);
    public static native void resizeNdsSave(int size);
}
