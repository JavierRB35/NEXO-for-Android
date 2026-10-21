#!/usr/bin/env python3
"""Fail CI if the installable pilot APK is missing QR scanner runtime classes.

This inspects DEX class definitions, not just dependency declarations or
references (which can exist even when a class is absent from the APK).
No Android emulator or external Python dependencies are required.
"""
import argparse
import struct
import sys
import zipfile


REQUIRED_DESCRIPTORS = {
    "Landroidx/core/content/ContextCompat;",
    "Lcom/journeyapps/barcodescanner/CaptureActivity;",
    "Lcom/journeyapps/barcodescanner/CaptureManager;",
    "Lcom/jruiz/nexo/connect/vault/NexoConnectQrScannerActivity;",
}


def u32(data: bytes, pos: int) -> int:
    if pos < 0 or pos + 4 > len(data):
        raise ValueError("Corrupt DEX offset")
    return struct.unpack_from("<I", data, pos)[0]


def read_uleb128(data: bytes, pos: int):
    value = 0
    for shift in (0, 7, 14, 21, 28):
        if pos >= len(data):
            raise ValueError("Truncated DEX string")
        byte = data[pos]
        pos += 1
        value |= (byte & 0x7f) << shift
        if byte < 128:
            return value, pos
    raise ValueError("Overlong DEX string")


def defined_classes(dex: bytes):
    if not dex.startswith(b"dex\\n") or len(dex) < 112:
        raise ValueError("Invalid DEX header")
    if u32(dex, 32) != len(dex):
        raise ValueError("DEX file size mismatch")
    string_count, string_off = u32(dex, 56), u32(dex, 60)
    type_count, type_off = u32(dex, 64), u32(dex, 68)
    class_count, class_off = u32(dex, 96), u32(dex, 100)
    if (string_off + string_count * 4 > len(dex)
            or type_off + type_count * 4 > len(dex)
            or class_off + class_count * 32 > len(dex)):
        raise ValueError("Corrupt DEX tables")

    for n in range(class_count):
        class_type = u32(dex, class_off + 32 * n)
        if class_type >= type_count:
            raise ValueError("Bad DEX class index")
        string_index = u32(dex, type_off + 4 * class_type)
        if string_index >= string_count:
            raise ValueError("Bad DEX type index")
        string_data_off = u32(dex, string_off + 4 * string_index)
        _, text_offset = read_uleb128(dex, string_data_off)
        terminator = dex.find(b"\\x00", text_offset)
        if terminator < 0:
            raise ValueError("Truncated DEX descriptor")
        yield dex[text_offset:terminator].decode("ascii", "strict")


def verify(apk_path: str):
    definitions = set()
    with zipfile.ZipFile(apk_path) as archive:
        dex_files = [
            name for name in archive.namelist()
            if name.startswith("classes") and name.endswith(".dex")
        ]
        if not dex_files:
            raise ValueError("APK contains no DEX files")
        for name in dex_files:
            definitions.update(defined_classes(archive.read(name)))

    missing = REQUIRED_DESCRIPTORS - definitions
    if missing:
        raise AssertionError("QR scanner runtime classes absent from APK: "
                             + ", ".join(sorted(missing)))
    print("QR scanner smoke check OK: "
          "ContextCompat, ZXing CaptureActivity/CaptureManager and Nexo QR "
          "activity are all defined in installable APK.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("apk")
    args = parser.parse_args()
    try:
        verify(args.apk)
    except (OSError, ValueError, AssertionError, zipfile.BadZipFile) as exc:
        print("QR runtime smoke check FAILED:", exc, file=sys.stderr)
        sys.exit(1)
