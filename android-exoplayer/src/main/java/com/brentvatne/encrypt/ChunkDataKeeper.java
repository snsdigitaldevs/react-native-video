package com.brentvatne.encrypt;

class ChunkDataKeeper {

    //缓存数据块
    private byte[] chunkData;

    //可用数据大小
    private int chunkRemaining;

    public ChunkDataKeeper(byte[] initData) {
        chunkData = new byte[initData.length];
        System.arraycopy(initData, 0, chunkData, 0, initData.length);
        chunkRemaining = chunkData.length;
    }

    public void appendChunk(byte[] data) {
        if (data == null) return;
        if (chunkData == null) {
            chunkData = new byte[0];
            chunkRemaining = 0;
        }
        //去掉已经使用过的数据
        byte[] c = new byte[chunkRemaining + data.length];
        System.arraycopy(chunkData, chunkData.length - chunkRemaining, c, 0, chunkRemaining);
        System.arraycopy(data, 0, c, chunkRemaining, data.length);
        chunkData = c;
        chunkRemaining = c.length;
    }

    public void readData(byte[] target, int offset, int readLength) {
        for (int i = 0; i < readLength; i++, chunkRemaining--) {
            final int targetIndex = offset + i;
            final int chunkDataIndex = chunkData.length - chunkRemaining;
            if (targetIndex >= target.length) {
                throw new ArrayIndexOutOfBoundsException("length=" + target.length + "; index=" + targetIndex);
            }
            if (chunkDataIndex >= chunkData.length) {
                throw new ArrayIndexOutOfBoundsException("length=" + chunkData.length + "; index=" + chunkDataIndex);
            }
            target[targetIndex] = chunkData[chunkDataIndex];
        }
    }

    public int remainingCount() {
        return chunkRemaining;
    }
}
