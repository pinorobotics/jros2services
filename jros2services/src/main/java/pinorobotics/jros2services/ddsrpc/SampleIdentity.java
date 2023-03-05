/*
 * Copyright 2022 jros2services project
 * 
 * Website: https://github.com/pinorobotics/jros2services
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
 * limitations under the License.
 */
package pinorobotics.jros2services.ddsrpc;

import java.nio.ByteBuffer;

/**
 * @author lambdaprime intid@protonmail.com
 */
public record SampleIdentity(byte[] writerGuid, long seqNum) {

    public byte[] toByteArray() {
        var buf = ByteBuffer.allocate(24);
        buf.put(writerGuid);
        var hi = (int) (seqNum >> 31);
        var lo = (int) ((-1L >> 31) & seqNum);
        buf.putInt(Integer.reverseBytes(hi));
        buf.putInt(Integer.reverseBytes(lo));
        return buf.array();
    }

    public static SampleIdentity valueOf(byte[] array) {
        var buf = ByteBuffer.wrap(array);
        var writerGuid = new byte[16];
        buf.get(writerGuid);
        long hi = Integer.reverseBytes(buf.getInt());
        long lo = Integer.reverseBytes(buf.getInt());
        var seqNum = ((hi << 31) | lo);
        return new SampleIdentity(writerGuid, seqNum);
    }
}
