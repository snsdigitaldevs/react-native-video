
package com.brentvatne.encrypt;

import android.net.Uri;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.BaseDataSource;
import com.google.android.exoplayer2.upstream.DataSpec;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import androidx.annotation.Nullable;

final class AesDataSource extends BaseDataSource {

    private static final int PER_CHUNK_SIZE = 1024;

    private final byte[] encryptionKey;
    private final byte[] encryptionIv;

    @Nullable
    private RandomAccessFile file;
    @Nullable
    private Uri uri;
    private long totalBytesRemaining;
    private boolean opened;
    private Cipher cipher;
    private ChunkDataKeeper currentRemainingData;

    public static class FileDataSourceException extends IOException {

        public FileDataSourceException(String message) {
            super(message);
        }

        public FileDataSourceException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public AesDataSource() throws IOException {
        super(false);
        final byte[] key = EncryptInfo.getInstance().getKey();
        final byte[] iv = EncryptInfo.getInstance().getIv();
        if (key == null || iv == null) throw new IOException("illegal aes args");
        this.encryptionKey = key;
        this.encryptionIv = iv;
    }

    @Override
    public long open(DataSpec dataSpec) throws FileDataSourceException {
        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(encryptionKey, "AES");
            IvParameterSpec ivParameterSpec = new IvParameterSpec(encryptionIv);
            cipher = Cipher.getInstance("AES/CFB/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);
            uri = dataSpec.uri;
            transferInitializing(dataSpec);
            file = new RandomAccessFile(dataSpec.uri.getPath(), "r");
            file.seek(dataSpec.position);
            prepareWithDataSpec(dataSpec);
            totalBytesRemaining = dataSpec.length == C.LENGTH_UNSET ? file.length() - dataSpec.position : dataSpec.length;
            if (totalBytesRemaining < 0) {
                throw new EOFException();
            }
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException | InvalidAlgorithmParameterException | IOException e) {
            e.printStackTrace();
            throw new FileDataSourceException(e.getMessage(), e);
        }

        opened = true;
        transferStarted(dataSpec);
        return totalBytesRemaining;
    }

    /**
     * 为 dataSpec Seek 功能提前准备
     *
     * @param dataSpec
     * @throws FileDataSourceException
     */
    private void prepareWithDataSpec(DataSpec dataSpec) throws FileDataSourceException {
        if (file == null) return;
        try {
            if (dataSpec.position == 0) {
                file.seek(0);
                return;
            }
            final long needBackSize = dataSpec.position % PER_CHUNK_SIZE;
            //只能seek到PER_CHUNK_SIZE整数倍
            file.seek(dataSpec.position - needBackSize);
            final long encryptDataLength = file.length() - dataSpec.position + needBackSize;
            byte[] decryptBytes;
            if (encryptDataLength > PER_CHUNK_SIZE) {
                decryptBytes = new byte[PER_CHUNK_SIZE];
                byte[] encryptBytes = new byte[PER_CHUNK_SIZE];
                file.read(encryptBytes, 0, PER_CHUNK_SIZE);
                cipher.update(encryptBytes, 0, encryptBytes.length, decryptBytes);
            } else {
                decryptBytes = new byte[(int) encryptDataLength];
                byte[] encryptBytes = new byte[(int) encryptDataLength];
                file.read(encryptBytes, 0, (int) encryptDataLength);
                cipher.doFinal(encryptBytes, 0, encryptBytes.length, decryptBytes);
            }
            currentRemainingData = new ChunkDataKeeper(decryptBytes);
        } catch (IOException | ShortBufferException | IllegalBlockSizeException | BadPaddingException e) {
            throw new FileDataSourceException(e.getMessage(), e);
        }
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws FileDataSourceException {
        if (readLength == 0 || file == null) {
            return 0;
        } else if (totalBytesRemaining == 0) {
            return C.RESULT_END_OF_INPUT;
        } else {
            //从已解密数据中获取
            if (currentRemainingData != null && currentRemainingData.remainingCount() > readLength) {
                currentRemainingData.readData(buffer, offset, readLength);
                internalByteTransferred(readLength);
                return readLength;
            }

            int bytesRead;
            try {
                int readLengthInChunkSize = (readLength / PER_CHUNK_SIZE + 1) * PER_CHUNK_SIZE;
                //是否是最后的数据
                boolean isLast = readLengthInChunkSize > totalBytesRemaining;
                int tempByteSize = isLast ? (int) totalBytesRemaining : readLengthInChunkSize;
                byte[] encryptBytes = new byte[tempByteSize];
                byte[] decryptBytes = new byte[tempByteSize];
                bytesRead = file.read(encryptBytes, 0, tempByteSize);
                if (bytesRead < tempByteSize) {
                    //处理实际读取比期望读取数据大的情况
                    byte[] resizeBytes = new byte[bytesRead];
                    System.arraycopy(encryptBytes, 0, resizeBytes, 0, bytesRead);
                    encryptBytes = resizeBytes;
                }
                if (isLast) {
                    cipher.doFinal(encryptBytes, 0, encryptBytes.length, decryptBytes);
                } else {
                    cipher.update(encryptBytes, 0, encryptBytes.length, decryptBytes);
                }

                if (currentRemainingData == null) {
                    currentRemainingData = new ChunkDataKeeper(decryptBytes);
                } else {
                    currentRemainingData.appendChunk(decryptBytes);
                }
                currentRemainingData.readData(buffer, offset, readLength);
            } catch (IOException | ShortBufferException | IllegalBlockSizeException | BadPaddingException e) {
                throw new FileDataSourceException(e.getMessage(), e);
            }
            internalByteTransferred(readLength);
            return readLength;
        }
    }

    private void internalByteTransferred(int bytesRead) {
        if (bytesRead > 0) {
            totalBytesRemaining -= bytesRead;
            bytesTransferred(bytesRead);
        }
    }

    @Override
    public void close() throws FileDataSourceException {
        uri = null;
        try {
            if (file != null) {
                file.close();
            }
            if (cipher != null) {
                cipher.doFinal();
            }
        } catch (BadPaddingException | IllegalBlockSizeException | IOException e) {
            throw new FileDataSourceException(e.getMessage(), e);
        } finally {
            file = null;
            cipher = null;
            if (opened) {
                opened = false;
                transferEnded();
            }
        }
    }

    @Override
    @Nullable
    public Uri getUri() {
        return uri;
    }
}
