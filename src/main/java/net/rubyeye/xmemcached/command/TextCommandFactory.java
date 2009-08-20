package net.rubyeye.xmemcached.command;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;

import net.rubyeye.xmemcached.CommandFactory;
import net.rubyeye.xmemcached.buffer.BufferAllocator;
import net.rubyeye.xmemcached.command.text.TextCASCommand;
import net.rubyeye.xmemcached.command.text.TextDeleteCommand;
import net.rubyeye.xmemcached.command.text.TextFlushAllCommand;
import net.rubyeye.xmemcached.command.text.TextGetMultiCommand;
import net.rubyeye.xmemcached.command.text.TextGetOneCommand;
import net.rubyeye.xmemcached.command.text.TextIncrDecrCommand;
import net.rubyeye.xmemcached.command.text.TextStatsCommand;
import net.rubyeye.xmemcached.command.text.TextStoreCommand;
import net.rubyeye.xmemcached.command.text.TextVerbosityCommand;
import net.rubyeye.xmemcached.command.text.TextVersionCommand;
import net.rubyeye.xmemcached.transcoders.Transcoder;
import net.rubyeye.xmemcached.utils.ByteUtils;

/**
 * Command Factory for creating text protocol commands.
 * 
 * @author dennis
 * 
 */
public final class TextCommandFactory implements CommandFactory {

	@Override
	public void setBufferAllocator(BufferAllocator bufferAllocator) {
		
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.rubyeye.xmemcached.CommandFactory#createDeleteCommand(java.lang.String
	 * , byte[], int)
	 */
	public final Command createDeleteCommand(final String key,
			final byte[] keyBytes, final int time, boolean noreply) {
		return new TextDeleteCommand(key, keyBytes, time,
				new CountDownLatch(1), noreply);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.rubyeye.xmemcached.CommandFactory#createVersionCommand()
	 */
	public final Command createVersionCommand(CountDownLatch latch,
			InetSocketAddress server) {
		return new TextVersionCommand(server, latch);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.rubyeye.xmemcached.CommandFactory#createFlushAllCommand(java.util
	 * .concurrent.CountDownLatch)
	 */
	public final Command createFlushAllCommand(CountDownLatch latch, int exptime,
			boolean noreply) {
		return new TextFlushAllCommand(latch, exptime, noreply);
	}

	/**
	 * Create verbosity command
	 * 
	 * @param latch
	 * @param level
	 * @param noreply
	 * @return
	 */
	public final Command createVerbosityCommand(CountDownLatch latch,
			int level, boolean noreply) {
		return new TextVerbosityCommand(latch, level, noreply);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @seenet.rubyeye.xmemcached.CommandFactory#createStatsCommand(java.net.
	 * InetSocketAddress, java.util.concurrent.CountDownLatch)
	 */
	public final Command createStatsCommand(InetSocketAddress server,
			CountDownLatch latch, String itemName) {
		return new TextStatsCommand(server, latch, itemName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.rubyeye.xmemcached.CommandFactory#createStoreCommand(java.lang.String
	 * , byte[], int, java.lang.Object,
	 * net.rubyeye.xmemcached.command.CommandType, java.lang.String, long,
	 * net.rubyeye.xmemcached.transcoders.Transcoder)
	 */
	@SuppressWarnings("unchecked")
	public final Command createCASCommand(final String key,
			final byte[] keyBytes, final int exp, final Object value, long cas,
			boolean noreply, Transcoder transcoder) {
		return new TextCASCommand(key, keyBytes, CommandType.CAS,
				new CountDownLatch(1), exp, cas, value, noreply, transcoder);
	}

	@SuppressWarnings("unchecked")
	public final Command createSetCommand(final String key,
			final byte[] keyBytes, final int exp, final Object value,
			boolean noreply, Transcoder transcoder) {
		return createStoreCommand(key, keyBytes, exp, value, CommandType.SET,
				noreply, transcoder);
	}

	@SuppressWarnings("unchecked")
	public final Command createAddCommand(final String key,
			final byte[] keyBytes, final int exp, final Object value,
			boolean noreply, Transcoder transcoder) {
		return createStoreCommand(key, keyBytes, exp, value, CommandType.ADD,
				noreply, transcoder);
	}

	@SuppressWarnings("unchecked")
	public final Command createReplaceCommand(final String key,
			final byte[] keyBytes, final int exp, final Object value,
			boolean noreply, Transcoder transcoder) {
		return createStoreCommand(key, keyBytes, exp, value,
				CommandType.REPLACE, noreply, transcoder);
	}

	@SuppressWarnings("unchecked")
	public final Command createAppendCommand(final String key,
			final byte[] keyBytes, final Object value, boolean noreply,
			Transcoder transcoder) {
		return createStoreCommand(key, keyBytes, 0, value, CommandType.APPEND,
				noreply, transcoder);
	}

	@SuppressWarnings("unchecked")
	public final Command createPrependCommand(final String key,
			final byte[] keyBytes, final Object value, boolean noreply,
			Transcoder transcoder) {
		return createStoreCommand(key, keyBytes, 0, value, CommandType.PREPEND,
				noreply, transcoder);
	}

	@SuppressWarnings("unchecked")
	final Command createStoreCommand(String key, byte[] keyBytes, int exp,
			Object value, CommandType cmdType, boolean noreply,
			Transcoder transcoder) {
		return new TextStoreCommand(key, keyBytes, cmdType, new CountDownLatch(
				1), exp, -1, value, noreply, transcoder);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.rubyeye.xmemcached.CommandFactory#createGetCommand(java.lang.String,
	 * byte[], net.rubyeye.xmemcached.command.CommandType)
	 */
	public final Command createGetCommand(final String key,
			final byte[] keyBytes, final CommandType cmdType) {
		return new TextGetOneCommand(key, keyBytes, cmdType,
				new CountDownLatch(1));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.rubyeye.xmemcached.CommandFactory#createGetMultiCommand(java.util
	 * .Collection, java.util.concurrent.CountDownLatch,
	 * net.rubyeye.xmemcached.command.CommandType,
	 * net.rubyeye.xmemcached.transcoders.Transcoder)
	 */
	public final <T> Command createGetMultiCommand(Collection<String> keys,
			CountDownLatch latch, CommandType cmdType, Transcoder<T> transcoder) {
		StringBuilder sb = new StringBuilder(keys.size() * 5);
		for (String tmpKey : keys) {
			ByteUtils.checkKey(tmpKey);
			sb.append(tmpKey).append(" ");
		}
		String gatherKey = sb.toString();
		byte[] keyBytes = ByteUtils.getBytes(gatherKey.substring(0, gatherKey
				.length() - 1));
		return new TextGetMultiCommand(keys.iterator().next(), keyBytes,
				cmdType, latch, transcoder);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.rubyeye.xmemcached.CommandFactory#createIncrDecrCommand(java.lang
	 * .String, byte[], int, net.rubyeye.xmemcached.command.CommandType)
	 */
	public final Command createIncrDecrCommand(final String key,
			final byte[] keyBytes, final int num, int init,
			int exptime, CommandType cmdType, boolean noreply) {
		return new TextIncrDecrCommand(key, keyBytes, cmdType,
				new CountDownLatch(1), num, noreply);
	}
}
