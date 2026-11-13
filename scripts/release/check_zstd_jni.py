#!/usr/bin/env python3
"""Reject release APKs whose DEX definitions break zstd's native field lookup.

Checks declared instance fields, not merely strings or field references. No
Android SDK or third-party Python packages needed. Run on the final R8 output.
"""
import struct
import sys
import zipfile


def declared_fields(data):
    if not data.startswith(b"dex\n"):
        raise ValueError("Not a standard DEX file")

    def uint(offset):
        return struct.unpack_from("<I", data, offset)[0]

    def uleb(offset):
        value = 0
        for shift in range(0, 35, 7):
            byte = data[offset]
            offset += 1
            value |= (byte & 127) << shift
            if not byte & 128:
                return value, offset
        raise ValueError("Invalid ULEB128")

    strings = []
    for i in range(uint(56)):
        offset = uint(uint(60) + 4 * i)
        _, offset = uleb(offset)
        strings.append(data[offset:data.index(b"\0", offset)].decode("utf-8", "replace"))
    types = [strings[uint(uint(68) + 4 * i)] for i in range(uint(64))]
    fields = []
    for i in range(uint(80)):
        owner, kind, name = struct.unpack_from("<HHI", data, uint(84) + 8 * i)
        fields.append((types[owner], strings[name], types[kind]))
    result = set()
    for i in range(uint(96)):
        offset = uint(uint(100) + 32 * i + 24)
        if not offset:
            continue
        counts = []
        for _ in range(4):
            count, offset = uleb(offset)
            counts.append(count)
        for group, count in enumerate(counts[:2]):
            index = 0
            for _ in range(count):
                delta, offset = uleb(offset)
                _, offset = uleb(offset)  # access flags
                index += delta
                if group == 1:  # instance fields; static fields cannot satisfy GetFieldID
                    result.add(fields[index])
    return result


def check(apk):
    fields = set()
    with zipfile.ZipFile(apk) as archive:
        for name in archive.namelist():
            if name.startswith("classes") and name.endswith(".dex"):
                fields.update(declared_fields(archive.read(name)))
    owner = "Lcom/github/luben/zstd/ZstdInputStreamNoFinalizer;"
    required = {(owner, name, "J") for name in ("srcPos", "dstPos")}
    missing = required - fields
    if missing:
        raise SystemExit("FAIL: zstd JNI fields missing/renamed: " + repr(sorted(missing)))
    print("PASS: release DEX declares ZstdInputStreamNoFinalizer.srcPos:J and dstPos:J")


if __name__ == "__main__":
    check(sys.argv[1])
