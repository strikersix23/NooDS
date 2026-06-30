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

#pragma once

#include "../core.h"
#include "../common/nds_icon.h"
#include "../common/screen_layout.h"

class CoreWrap {
public:
    static int showFpsCounter;

    CoreWrap(const char *path);
    int getFps() const { return core->fps; }
    bool getGbaMode() const { return core->gbaMode; }

    void updateFrame() const;
    void getSamples(void *buffer, uint32_t count) const;
    void pressKey(int key) const { core->input.pressKey(key); }
    void releaseKey(int key) const { core->input.releaseKey(key); }
    void pressScreen(int x, int y) const;
    void releaseScreen() const;
    
    static bool loadSettings(const char *path);
    static int bytesCb(void *info, void *buffer, int count);
    static int forwardCb(void *info, int count);
    static void rewindCb(void *info);

private:
    Core *core;
    std::thread *thread;
};
