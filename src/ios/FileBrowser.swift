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

struct FileRow: View {
    let name: String
    let icon: Image

    @Environment(\.colorScheme) var scheme

    init(path: String, name: String, folder: Bool) {
        // Set the name and icon for a file row
        self.name = name
        if !folder && name.hasSuffix(".nds") {
            // Use an icon extracted from a .nds file
            var ndsIcon = NdsIcon(path + "/" + name)
            let space = CGColorSpaceCreateDeviceRGB()
            let info = CGBitmapInfo(alpha: .noneSkipLast, component: .integer, byteOrder: .orderDefault)
            let prov = CGDataProvider(data: CFDataCreate(nil, ndsIcon.getIcon(), 32 * 32 * 4)!)!
            let deco = CGImage(width: 32, height: 32, bitsPerComponent: 8, bitsPerPixel: 32,
                bytesPerRow: 32 * 4, space: space, bitmapInfo: info, provider: prov,
                decode: nil, shouldInterpolate: false, intent: .defaultIntent)!
            icon = Image(decorative: deco, scale: 1.0, orientation: .up).resizable()
        }
        else {
            // Use a generic file or folder icon
            icon = Image(folder ? "Folder" : "File").resizable()
                .renderingMode(.template)
        }
    }

    var body: some View {
        // List a file/folder with an icon and name
        HStack {
            icon.frame(width: 40, height: 40)
            Text(name)
            Spacer()
        }
        .foregroundColor(scheme == .dark ? .white : .black)
    }
}

struct FileBrowser: View {
    @Binding var running: Bool
    @Binding var path: String
    @State var path2: String
    @State var contents: [String]
    let manager = FileManager.default
    let base: String

    init(running: Binding<Bool>, path: Binding<String>) {
        // Initialize values for the base directory
        let docsUrl = manager.urls(for: .documentDirectory, in: .userDomainMask).first!
        _running = running
        _path = path
        path2 = docsUrl.path
        contents = try! manager.contentsOfDirectory(atPath: docsUrl.path).sorted()
        base = docsUrl.path
    }

    var body: some View {
        NavigationView {
            List {
                // Add a parent directory listing when outside the base
                if path2 != base {
                    Button {
                        // Navigate to the parent directory
                        path2 = String(path2[..<path2.lastIndex(of: "/")!])
                        contents = try! manager.contentsOfDirectory(atPath: path2).sorted()
                    }
                    label: {
                        FileRow(path: path2, name: "..", folder: true)
                    }
                }

                // List all folders and ROMs at the current directory
                ForEach(contents, id: \.self) { content in
                    var isDir = false as ObjCBool
                    let folder = manager.fileExists(atPath: path2 + "/" + content, isDirectory: &isDir) && isDir.boolValue
                    if folder || content.hasSuffix(".nds") || content.hasSuffix(".gba") {
                        Button {
                            // Navigate to a selected folder or run a selected ROM
                            path2 += "/" + content
                            if folder {
                                contents = try! manager.contentsOfDirectory(atPath: path2).sorted()
                            }
                            else {
                                running = true
                                path = path2
                            }
                        }
                        label: {
                            FileRow(path: path2, name: content, folder: folder)
                        }
                    }
                }
            }
            .navigationTitle("NooDS")
            .toolbar {
                // Link to the settings menu in the toolbar
                NavigationLink {
                    SettingsMenu()
                }
                label: {
                    Image(systemName: "gearshape.fill")
                }
            }
        }
    }
}
