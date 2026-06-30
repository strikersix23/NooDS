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

import SwiftUI

struct SettingsHeader: View {
    let title: String

    var body: some View {
        // Show a category header with bottom-left positioning
        VStack {
            Spacer()
            HStack {
                Text(title).font(.subheadline).foregroundColor(.gray)
                Spacer()
            }
        }
    }
}

struct SettingsToggle: View {
    let title: String
    let value: UnsafeMutablePointer<CInt>
    @State var state: Bool

    init(title: String, value: UnsafeMutablePointer<CInt>) {
        // Set values and initial toggle state
        self.title = title
        self.value = value
        self.state = value.pointee != 0
    }

    var body: some View {
        // Show a toggle that updates and saves a binary setting
        Toggle(title, isOn: $state)
        .onChange(of: state) { _ in
            value.pointee = state ? 1 : 0
            Settings.save()
        }
    }
}

struct SettingsPicker: View {
    let title: String
    let value: UnsafeMutablePointer<CInt>
    let labels: [String]
    @State var select: Int

    init(title: String, value: UnsafeMutablePointer<CInt>, labels: [String]) {
        // Set values and initial picker selection
        self.title = title
        self.value = value
        self.labels = labels
        self.select = Int(value.pointee)
    }

    var body: some View {
        // Show a picker that updates and saves a multi-choice setting
        Picker(title, selection: $select) {
            ForEach(labels.indices) { i in
                Text(labels[i]).tag(i)
            }
        }
        .onChange(of: select) { _ in
            value.pointee = CInt(select)
            Settings.save()
        }
    }
}

struct SettingsMenu: View {
    var body: some View {
        List {
            // List the general settings category
            SettingsHeader(title: "General Settings")
            SettingsToggle(title: "Direct Boot", value: &Settings.directBoot)
            SettingsToggle(title: "Keep ROM in RAM", value: &Settings.romInRam)
            SettingsToggle(title: "FPS Limiter", value: &Settings.fpsLimiter)
            SettingsToggle(title: "Show FPS Counter", value: &CoreWrap.showFpsCounter)

            // List the graphics settings category
            SettingsHeader(title: "Graphics Settings")
            SettingsPicker(title: "Skip Frames", value: &Settings.frameskip,
                labels: ["None", "1 Frame", "2 Frames", "3 Frames", "4 Frames", "5 Frames"])
            SettingsToggle(title: "Threaded 2D", value: &Settings.threaded2D)
            SettingsPicker(title: "Threaded 3D", value: &Settings.threaded3D,
                labels: ["Disabled", "1 Thread", "2 Threads", "3 Threads", "4 Threads"])
            SettingsToggle(title: "High-Resolution 3D", value: &Settings.highRes3D)
            SettingsToggle(title: "Simulate Ghosting", value: &Settings.screenGhost)

            // List the audio settings category
            SettingsHeader(title: "Audio Settings")
            SettingsToggle(title: "Audio Emulation", value: &Settings.emulateAudio)
            SettingsToggle(title: "16-Bit Audio Output", value: &Settings.audio16Bit)

            // List the experimental settings category
            SettingsHeader(title: "Experimental Settings")
            SettingsToggle(title: "High-Level ARM7", value: &Settings.arm7Hle)
            SettingsToggle(title: "DSi Homebrew Mode", value: &Settings.dsiMode)

            // List the path settings category
            SettingsHeader(title: "Path Settings")
            SettingsToggle(title: "Separate Saves Folder", value: &Settings.savesFolder)
            SettingsToggle(title: "Separate States Folder", value: &Settings.statesFolder)
            SettingsToggle(title: "Separate Cheats Folder", value: &Settings.cheatsFolder)

            // List the screen layout category
            SettingsHeader(title: "Screen Layout")
            SettingsPicker(title: "Screen Position", value: &ScreenLayout.screenPosition,
                labels: ["Center", "Top", "Bottom", "Left", "Right"])
            SettingsPicker(title: "Screen Rotation", value: &ScreenLayout.screenRotation,
                labels: ["None", "Clockwise", "Counter-Clockwise"])
            SettingsPicker(title: "Screen Arrangement", value: &ScreenLayout.screenArrangement,
                labels: ["Automatic", "Vertical", "Horizontal", "Single Screen"])
            SettingsPicker(title: "Screen Sizing", value: &ScreenLayout.screenSizing,
                labels: ["Even", "Enlarge Top", "Enlarge Bottom"])
            SettingsPicker(title: "Screen Gap", value: &ScreenLayout.screenGap,
                labels: ["None", "Quarter", "Half", "Full"])
            SettingsPicker(title: "Screen Filter", value: &Settings.screenFilter,
                labels: ["Nearest", "Upscaled", "Linear"])
            SettingsPicker(title: "Aspect Ratio", value: &ScreenLayout.aspectRatio,
                labels: ["Default", "16:10", "16:9", "18:9"])
            SettingsToggle(title: "Integer Scale", value: &ScreenLayout.integerScale)
            SettingsToggle(title: "GBA Crop", value: &ScreenLayout.gbaCrop)
        }
        .navigationTitle("Settings")
    }
}
