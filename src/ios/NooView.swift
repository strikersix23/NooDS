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
import AVFAudio

struct NooView: View {
    @State var core: CoreWrap
    let audio = AVAudioEngine()
    let space = CGColorSpaceCreateDeviceRGB()
    let info = CGBitmapInfo(alpha: .noneSkipLast, component: .integer, byteOrder: .orderDefault)
    let topProv: CGDataProvider
    let botProv: CGDataProvider
    var ids = [CChar]([0, 1])

    @Environment(\.displayScale) var scale: CGFloat

    init(path: String) {
        // Initialize the core and top/bottom framebuffer providers
        var cbs = CGDataProviderSequentialCallbacks(version: 0, getBytes: bytesCb, skipForward: forwardCb, rewind: rewindCb, releaseInfo: nil)
        topProv = CGDataProvider(sequentialInfo: &ids[0], callbacks: &cbs)!
        botProv = CGDataProvider(sequentialInfo: &ids[1], callbacks: &cbs)!
        core = CoreWrap(path)

        // Configure the audio session for playback
        let session = AVAudioSession.sharedInstance()
        try! session.setCategory(.playback, mode: .moviePlayback)
        try! session.setActive(true)

        // Hook up the audio callback and start playing
        let format = AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: 44100, channels: 2, interleaved: true)!
        let source = AVAudioSourceNode(format: format, renderBlock: audioCb)
        audio.attach(source)
        audio.connect(source, to: audio.outputNode, format: format)
        audio.prepare()
        try! audio.start()
    }

    var body: some View {
        ZStack {
            // Redraw the display on a canvas at 60 FPS
            var layout = ScreenLayout()
            TimelineView(.periodic(from: Date(), by: 1.0 / 60)) { _ in
                ZStack {
                    Canvas { context, size in
                        // Update the layout with current canvas dimensions scaled to pixels
                        let gbaMode = core.getGbaMode() && ScreenLayout.gbaCrop != 0
                        layout.update(Int32(size.width * scale), Int32(size.height * scale), gbaMode)
                        let orient = ([Image.Orientation])([.up, .right, .left])[Int(min(ScreenLayout.screenRotation, 2))]
                        let interp = (Settings.screenFilter == 0) ? Image.Interpolation.none : Image.Interpolation.high
                        let shift = (Settings.highRes3D != 0 || Settings.screenFilter == 1) ? 1 : 0
                        core.updateFrame()

                        // Draw screens depending on the configuration
                        if gbaMode {
                            // Get the GBA screen buffer and draw it
                            let gbaBuf = CGImage(width: 240 << shift, height: 160 << shift, bitsPerComponent: 8,
                                bitsPerPixel: 32, bytesPerRow: (240 * 4) << shift, space: space, bitmapInfo: info,
                                provider: topProv, decode: nil, shouldInterpolate: false, intent: .defaultIntent)!
                            let gbaImg = Image(decorative: gbaBuf, scale: 1.0, orientation: orient).interpolation(interp)
                            let gbaRect = CGRect(x: CGFloat(layout.topX) / scale, y: CGFloat(layout.topY) / scale,
                                width: CGFloat(layout.topWidth) / scale, height: CGFloat(layout.topHeight) / scale)
                            context.draw(gbaImg, in: gbaRect, style: FillStyle())
                        }
                        else {
                            // Get the top screen buffer and draw it
                            if ScreenLayout.screenArrangement != 3 || ScreenLayout.screenSizing < 2 {
                                let topBuf = CGImage(width: 256 << shift, height: 192 << shift, bitsPerComponent: 8,
                                    bitsPerPixel: 32, bytesPerRow: (256 * 4) << shift, space: space, bitmapInfo: info,
                                    provider: topProv, decode: nil, shouldInterpolate: false, intent: .defaultIntent)!
                                let topImg = Image(decorative: topBuf, scale: 1.0, orientation: orient).interpolation(interp)
                                let topRect = CGRect(x: CGFloat(layout.topX) / scale, y: CGFloat(layout.topY) / scale,
                                    width: CGFloat(layout.topWidth) / scale, height: CGFloat(layout.topHeight) / scale)
                                context.draw(topImg, in: topRect, style: FillStyle())
                            }

                            // Get the bottom screen buffer and draw it
                            if ScreenLayout.screenArrangement != 3 || ScreenLayout.screenSizing == 2 {
                                let botBuf = CGImage(width: 256 << shift, height: 192 << shift, bitsPerComponent: 8,
                                    bitsPerPixel: 32, bytesPerRow: (256 * 4) << shift, space: space, bitmapInfo: info,
                                    provider: botProv, decode: nil, shouldInterpolate: false, intent: .defaultIntent)!
                                let botImg = Image(decorative: botBuf, scale: 1.0, orientation: orient).interpolation(interp)
                                let botRect = CGRect(x: CGFloat(layout.botX) / scale, y: CGFloat(layout.botY) / scale,
                                    width: CGFloat(layout.botWidth) / scale, height: CGFloat(layout.botHeight) / scale)
                                context.draw(botImg, in: botRect, style: FillStyle())
                            }
                        }
                    }
                    .simultaneousGesture(DragGesture(minimumDistance: 0)
                        .onChanged({ touch in
                            // Send a touch press to the core, with coordinates relative to the layout
                            let touchX = layout.getTouchX(Int32(touch.location.x * scale), Int32(touch.location.y * scale))
                            let touchY = layout.getTouchY(Int32(touch.location.x * scale), Int32(touch.location.y * scale))
                            core.pressScreen(touchX, touchY)
                        })
                        .onEnded({ _ in
                            // Send a touch release to the core
                            core.releaseScreen()
                        })
                    )

                    // Show the FPS counter in the top-left corner with some padding
                    VStack {
                        HStack {
                            Text(String("\(core.getFps()) FPS"))
                                .foregroundColor(.white)
                                .font(.title)
                            Spacer()
                        }.padding(.all, 5)
                        Spacer()
                    }.padding(.all, 5)
                }
            }.background(.black)

            // Create an on-screen controller above the display
            VStack {
                Spacer()
                HStack {
                    NooButton(core: $core, ids: [9], name: "L").frame(width: 110, height: 44)
                    Spacer()
                    NooButton(core: $core, ids: [8], name: "R").frame(width: 110, height: 44)
                }.padding(.all, 5)
                Spacer().frame(width: 110, height: 44)
                HStack {
                    NooButton(core: $core, ids: [4, 5, 6, 7], name: "Dpad").frame(width: 132, height: 132)
                    Spacer()
                    NooButton(core: $core, ids: [0, 11, 10, 1], name: "Abxy").frame(width: 165, height: 165)
                }.padding(.all, 5)
                HStack {
                    NooButton(core: $core, ids: [2], name: "Select").frame(width: 33, height: 33)
                    Spacer().frame(width: 33, height: 33)
                    NooButton(core: $core, ids: [3], name: "Start").frame(width: 33, height: 33)
                }.padding(.all, 5)
            }
        }
    }

    func audioCb(isSilence: UnsafeMutablePointer<ObjCBool>, timestamp: UnsafePointer<AudioTimeStamp>,
        frameCount: AVAudioFrameCount, outputData: UnsafeMutablePointer<AudioBufferList>) -> OSStatus {
        // Get the core to fill the audio buffer
        core.getSamples(outputData.pointee.mBuffers.mData, frameCount)
        return noErr
    }
}

func bytesCb(info: UnsafeMutableRawPointer?, buffer: UnsafeMutableRawPointer, count: Int) -> Int {
    // Forward the bytes callback to the C++ bridge
    return Int(bytesBridge(info, buffer, Int32(count)))
}

func forwardCb(info: UnsafeMutableRawPointer?, count: off_t) -> off_t {
    // Forward the forward callback to the C++ bridge
    return off_t(forwardBridge(info, Int32(count)))
}

func rewindCb(info: UnsafeMutableRawPointer?) -> Void {
    // Forward the rewind callback to the C++ bridge
    return rewindBridge(info)
}
