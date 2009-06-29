package net.rubyeye.xmemcached.command.text;

import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

import net.rubyeye.xmemcached.buffer.BufferAllocator;
import net.rubyeye.xmemcached.command.CommandType;
import net.rubyeye.xmemcached.command.StoreCommand;
import net.rubyeye.xmemcached.impl.MemcachedTCPSession;
import net.rubyeye.xmemcached.monitor.Constants;
import net.rubyeye.xmemcached.transcoders.CachedData;
import net.rubyeye.xmemcached.transcoders.Transcoder;
import net.rubyeye.xmemcached.utils.ByteUtils;

public class TextStoreCommand extends StoreCommand {

	@SuppressWarnings("unchecked")
	public TextStoreCommand(String key, byte[] keyBytes, CommandType cmdType,
			CountDownLatch latch, int exp, long cas, Object value,
			boolean noreply, Transcoder transcoder) {
		super(key, keyBytes, cmdType, latch, exp, cas, value, noreply,
				transcoder);
	}

	@Override
	public boolean decode(MemcachedTCPSession session, ByteBuffer buffer) {
		if (buffer == null || !buffer.hasRemaining())
			return false;
		if (result == null) {
			byte first = buffer.get(buffer.position());
			if (first == 'S') {
				setResult(Boolean.TRUE);
				countDownLatch();
				// STORED\r\n
				return ByteUtils.stepBuffer(buffer, 8);
			} else if (first == 'N') {
				setResult(Boolean.FALSE);
				countDownLatch();
				// NOT_STORED\r\n
				return ByteUtils.stepBuffer(buffer, 12);
			} else
				return decodeError(session, buffer);
		} else {
			Boolean result = (Boolean) this.result;
			if (result) {
				return ByteUtils.stepBuffer(buffer, 8);
			} else {
				return ByteUtils.stepBuffer(buffer, 12);
			}
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public final void encode(BufferAllocator bufferAllocator) {
		final CachedData data = transcoder.encode(value);
		byte[] flagBytes = ByteUtils.getBytes(String.valueOf(data.getFlags()));
		byte[] expBytes = ByteUtils.getBytes(String.valueOf(exp));
		byte[] dataLenBytes = ByteUtils.getBytes(String
				.valueOf(data.getData().length));
		byte[] casBytes = ByteUtils.getBytes(String.valueOf(cas));
		String cmdStr = this.commandType.name().toLowerCase();
		int size = cmdStr.length() + 1 + keyBytes.length + 1 + flagBytes.length
				+ 1 + expBytes.length + 1 + data.getData().length + 2
				* Constants.CRLF.length + dataLenBytes.length;
		if (this.commandType == CommandType.CAS) {
			size += 1 + casBytes.length;
		}
		if (isNoreply())
			this.ioBuffer = bufferAllocator.allocate(size + 1
					+ Constants.NO_REPLY.length());
		else
			this.ioBuffer = bufferAllocator.allocate(size);
		if (this.commandType == CommandType.CAS) {
			if (isNoreply())
				ByteUtils.setArguments(this.ioBuffer, cmdStr, keyBytes,
						flagBytes, expBytes, dataLenBytes, casBytes,
						Constants.NO_REPLY);
			else
				ByteUtils.setArguments(this.ioBuffer, cmdStr, keyBytes,
						flagBytes, expBytes, dataLenBytes, casBytes);
		} else {
			if (isNoreply())
				ByteUtils.setArguments(this.ioBuffer, cmdStr, keyBytes,
						flagBytes, expBytes, dataLenBytes, Constants.NO_REPLY);
			else
				ByteUtils.setArguments(this.ioBuffer, cmdStr, keyBytes,
						flagBytes, expBytes, dataLenBytes);
		}
		ByteUtils.setArguments(this.ioBuffer, data.getData());

		this.ioBuffer.flip();
	}

}
