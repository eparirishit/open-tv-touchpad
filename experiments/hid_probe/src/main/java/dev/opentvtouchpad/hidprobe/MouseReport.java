/*
 * Descriptor adapted from experiments/hub_remote/pointer.py HardwarePointer,
 * originally scrcpy v5.0.1 app/src/hid/hid_mouse.c:
 * https://github.com/Genymobile/scrcpy/blob/v5.0.1/app/src/hid/hid_mouse.c
 * Copyright (C) 2018 Genymobile
 * Copyright (C) 2018-2026 Romain Vimont
 * Changed here to report ID 1, three buttons and five padding bits.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. Full copy: experiments/hub_remote/scrcpy-LICENSE.
 */
package dev.opentvtouchpad.hidprobe;

final class MouseReport {
    static final int ID = 1;
    static final int SIZE = 5;
    enum Direction { UP, DOWN, LEFT, RIGHT }

    private MouseReport() { }

    static byte[] descriptor() {
        return new byte[] {
            0x05, 0x01, 0x09, 0x02, (byte) 0xa1, 0x01,
            (byte) 0x85, ID, 0x09, 0x01, (byte) 0xa1, 0x00,
            0x05, 0x09, 0x19, 0x01, 0x29, 0x03, 0x15, 0x00, 0x25, 0x01,
            (byte) 0x95, 0x03, 0x75, 0x01, (byte) 0x81, 0x02,
            (byte) 0x95, 0x01, 0x75, 0x05, (byte) 0x81, 0x01,
            0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x09, 0x38,
            0x15, (byte) 0x81, 0x25, 0x7f, 0x75, 0x08, (byte) 0x95, 0x03, (byte) 0x81, 0x06,
            0x05, 0x0c, 0x0a, 0x38, 0x02, 0x15, (byte) 0x81, 0x25, 0x7f,
            0x75, 0x08, (byte) 0x95, 0x01, (byte) 0x81, 0x06, (byte) 0xc0, (byte) 0xc0
        };
    }

    // Android takes ID separately; payloads contain only buttons, X, Y, wheel, pan.
    static byte[] neutral() { return new byte[SIZE]; }

    static byte[] move(int dx, int dy) {
        if (dx < -127 || dx > 127 || dy < -127 || dy > 127) {
            throw new IllegalArgumentException("Mouse delta out of bounds");
        }
        return new byte[] {0, (byte) dx, (byte) dy, 0, 0};
    }

    static byte[] clickDown() { return new byte[] {1, 0, 0, 0, 0}; }

    static byte[] scroll(Direction direction) {
        if (direction == null) throw new IllegalArgumentException("Scroll direction is required");
        return switch (direction) {
            case UP -> new byte[] {0, 0, 0, 1, 0};
            case DOWN -> new byte[] {0, 0, 0, -1, 0};
            case LEFT -> new byte[] {0, 0, 0, 0, -1};
            case RIGHT -> new byte[] {0, 0, 0, 0, 1};
        };
    }

    static boolean acceptsGetReport(int type, int id, int bufferSize) {
        return type == 1 && id == ID && (bufferSize == 0 || bufferSize >= SIZE);
    }
}
