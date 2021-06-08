package com.google.finapp;

import com.google.cloud.ByteArray;

import java.nio.ByteBuffer;
import java.util.UUID;

final class UuidConverter {

  static ByteArray getBytesFromUuid(UUID uuid) {
    ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
    bb.putLong(uuid.getMostSignificantBits());
    bb.putLong(uuid.getLeastSignificantBits());
    return ByteArray.copyFrom(bb.array());
  }

}
