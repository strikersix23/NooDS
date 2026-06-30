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

#include "bridge.h"

int CoreWrap::showFpsCounter = 0;
static uint32_t framebuffer[256 * 192 * 8];
static uint32_t *fbCur[] = { framebuffer, framebuffer };

static void runCore(Core *core) {
    // Run the emulator
    while (core)
        core->runCore();
}

CoreWrap::CoreWrap(const char *path) {
    // Initialize the core with a NDS or GBA ROM based on file extension
    std::string path2 = path;
    if (path2.find(".nds", path2.length() - 4) != std::string::npos)
        core = new Core(path2, "");
    else if (path2.find(".gba", path2.length() - 4) != std::string::npos)
        core = new Core("", path2);
    else
        core = new Core();
    thread = new std::thread(&runCore, core);
}

void CoreWrap::updateFrame() const {
    // Update the framebuffer used by the image callbacks
    bool gba = core->gbaMode && ScreenLayout::gbaCrop;
    core->gpu.getFrame(framebuffer, gba);
}

void CoreWrap::getSamples(void *buffer, uint32_t count) const {
    // Fill an audio buffer with core data resampled to 44100Hz
    uint32_t scale = (count & ~0x1) * 32768 / 44100; // Rounded for consistency
    uint32_t *samples = core->spu.getSamples(scale);
    for (int i = 0; i < count; i++)
        ((uint32_t*)buffer)[i] = samples[i * scale / count];
    delete[] samples;
}

void CoreWrap::pressScreen(int x, int y) const {
    // Press the screen and set coordinates
    core->input.pressScreen();
    core->spi.setTouch(x, y);
}

void CoreWrap::releaseScreen() const {
    // Release the screen and clear coordinates
    core->input.releaseScreen();
    core->spi.clearTouch();
}

bool CoreWrap::loadSettings(const char *path) {
    // Define and add the platform settings
    std::vector<Setting> platformSettings = {
        Setting("showFpsCounter", &showFpsCounter, false),
    };
    ScreenLayout::addSettings();
    Settings::add(platformSettings);

    // Load settings and update path prefixes in case the app UUID changed
    std::string path2 = path;
    if (!Settings::load(path2)) return false;
    strncpy(&Settings::bios9Path[0], path, path2.size() - 1);
    strncpy(&Settings::bios7Path[0], path, path2.size() - 1);
    strncpy(&Settings::firmwarePath[0], path, path2.size() - 1);
    strncpy(&Settings::gbaBiosPath[0], path, path2.size() - 1);
    strncpy(&Settings::sdImagePath[0], path, path2.size() - 1);
    return true;
}

int CoreWrap::bytesCb(void *info, void *buffer, int count) {
    // Copy top/bottom framebuffer bytes to an image and move the pointer
    uint8_t i = *(uint8_t*)info;
    int s = (Settings::highRes3D || Settings::screenFilter == 1) ? 2 : 0;
    uintptr_t end = uintptr_t(framebuffer + ((256 * 192 * (i + 1)) << s));
    count = std::max(0, std::min<int>(end - uintptr_t(fbCur[i]), count));
    memcpy(buffer, fbCur[i], count);
    fbCur[i] = (uint32_t*)((uint8_t*)fbCur[i] + count);
    return count;
}

int CoreWrap::forwardCb(void *info, int count) {
    // Move the top/bottom framebuffer pointer forward
    uint8_t i = *(uint8_t*)info;
    int s = (Settings::highRes3D || Settings::screenFilter == 1) ? 2 : 0;
    uintptr_t end = uintptr_t(framebuffer + ((256 * 192 * (i + 1)) << s));
    count = std::max(0, std::min<int>(end - uintptr_t(fbCur[i]), count));
    fbCur[i] = (uint32_t*)((uint8_t*)fbCur[i] + count);
    return count;
}

void CoreWrap::rewindCb(void *info) {
    // Reset the top/bottom framebuffer pointer
    uint8_t i = *(uint8_t*)info;
    int s = (Settings::highRes3D || Settings::screenFilter == 1) ? 2 : 0;
    fbCur[i] = framebuffer + ((256 * 192 * i) << s);
}
