package net.rubyeye.xmemcached.test.unittest;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import junit.framework.TestCase;
import net.rubyeye.xmemcached.CASOperation;
import net.rubyeye.xmemcached.GetsResponse;
import net.rubyeye.xmemcached.MemcachedClient;
import net.rubyeye.xmemcached.MemcachedClientBuilder;
import net.rubyeye.xmemcached.XMemcachedClient;
import net.rubyeye.xmemcached.XMemcachedClientBuilder;
import net.rubyeye.xmemcached.buffer.BufferAllocator;
import net.rubyeye.xmemcached.command.Command;
import net.rubyeye.xmemcached.command.CommandType;
import net.rubyeye.xmemcached.exception.MemcachedException;
import net.rubyeye.xmemcached.exception.UnknownCommandException;
import net.rubyeye.xmemcached.impl.MemcachedTCPSession;
import net.rubyeye.xmemcached.transcoders.StringTranscoder;
import net.rubyeye.xmemcached.utils.AddrUtil;
import net.rubyeye.xmemcached.utils.ByteUtils;

import com.google.code.yanf4j.util.ResourcesUtils;

public abstract class XMemcachedClientTest extends TestCase {
	MemcachedClient memcachedClient;
	Properties properties;

	@Override
	public void setUp() throws Exception {
		createClients();
		//windows上的memcached实现有问题，除非加上日志显示，否则可能不返回
		this.memcachedClient.setLoggingLevelVerbosity(new InetSocketAddress(
				"localhost", 12000), 3);
	}

	public void testCreateClientWithEmptyServers() throws Exception {
		MemcachedClient client = new XMemcachedClient();
		assertFalse(client.isShutdown());
		client.shutdown();
		assertTrue(client.isShutdown());

		MemcachedClientBuilder builder = new XMemcachedClientBuilder();
		client = builder.build();
		assertFalse(client.isShutdown());
		client.shutdown();
		assertTrue(client.isShutdown());
	}

	protected void createClients() throws IOException, Exception,
			TimeoutException, InterruptedException, MemcachedException {
		properties = ResourcesUtils.getResourceAsProperties("test.properties");

		MemcachedClientBuilder builder = createBuilder();
		builder.getConfiguration().setStatisticsServer(true);
		memcachedClient = builder.build();
		memcachedClient.flushAllWithNoReply();
	}

	public abstract MemcachedClientBuilder createBuilder() throws Exception;

	public abstract MemcachedClientBuilder createWeightedBuilder()
			throws Exception;

	public void testGet() throws Exception {
		try {
			assertNull(memcachedClient.get("name"));
		} catch (Exception e) {

		}
		memcachedClient.set("name", 1, "dennis", new StringTranscoder(), 1000);
		assertEquals("dennis", memcachedClient.get("name",
				new StringTranscoder()));
		Thread.sleep(2000);
		// expire
		assertNull(memcachedClient.get("name"));
		// blank key
		try {
			memcachedClient.get("");
			fail();
		} catch (IllegalArgumentException e) {
			assertEquals("Key must not be blank", e.getMessage());
		}
		// null key
		try {
			memcachedClient.get((String) null);
			fail();
		} catch (IllegalArgumentException e) {
			assertEquals("Key must not be blank", e.getMessage());
		}
		// invalid key
		try {
			memcachedClient.get("test\r\n");
			fail();
		} catch (IllegalArgumentException e) {
			assertTrue(e.getMessage().startsWith(
					"Key contains invalid characters"));
		}
		try {
			memcachedClient.get("test test2");
			fail();
		} catch (IllegalArgumentException e) {
			assertTrue(e.getMessage().startsWith(
					"Key contains invalid characters"));
		}
		// key is too long
		try {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < 256; i++)
				sb.append(i);
			memcachedClient.get(sb.toString());
			fail();
		} catch (IllegalArgumentException e) {
			assertEquals("Key is too long (maxlen = 250)", e.getMessage());
		}
		// client is shutdown
		try {
			memcachedClient.shutdown();
			memcachedClient.get("name");
			fail();
		} catch (IllegalStateException e) {
			assertEquals("Xmemcached is stopped", e.getMessage());
		}
	}

	public void testStore() throws Exception {
		// add,replace
		assertTrue(memcachedClient.add("name", 0, "dennis"));
		assertFalse(memcachedClient.add("name", 0, "dennis"));
		assertEquals("dennis", memcachedClient.get("name", 2000));

		assertFalse(memcachedClient.replace("unknownKey", 0, "test"));
		assertTrue(memcachedClient.replace("name", 1, "zhuang"));
		assertEquals("zhuang", memcachedClient.get("name", 2000));
		Thread.sleep(2000);
		assertNull(memcachedClient.get("zhuang"));
		// set
		assertTrue(memcachedClient.set("name", 0, "dennis"));
		assertEquals("dennis", memcachedClient.get("name", 2000));

		assertTrue(memcachedClient.set("name", 1, "zhuang",
				new StringTranscoder()));
		assertEquals("zhuang", memcachedClient.get("name", 2000));
		Thread.sleep(2000);
		assertNull(memcachedClient.get("zhuang"));

		// append,prepend
		assertTrue(memcachedClient.set("name", 0, "dennis",
				new StringTranscoder(), 1000));
		assertTrue(memcachedClient.prepend("name", "hello "));
		assertEquals("hello dennis", memcachedClient.get("name"));
		assertTrue(memcachedClient.append("name", " zhuang"));
		assertEquals("hello dennis zhuang", memcachedClient.get("name"));
		memcachedClient.delete("name");
		assertFalse(memcachedClient.prepend("name", "hello ", 2000));
		assertFalse(memcachedClient.append("name", " zhuang", 2000));

		// store list
		List<String> list = new ArrayList<String>();
		for (int i = 0; i < 100; i++)
			list.add(String.valueOf(i));
		assertTrue(memcachedClient.add("list", 0, list));
		List<String> result = memcachedClient.get("list");
		assertEquals(100, result.size());

		for (int i = 0; i < result.size(); i++) {
			assertEquals(list.get(i), result.get(i));
		}
	}

	public void testStoreNoReply() throws Exception {
		memcachedClient.setWithNoReply("name", 1, "dennis",
				new StringTranscoder());
		assertEquals("dennis", memcachedClient.get("name"));
		Thread.sleep(2000);
		assertNull(memcachedClient.get("name"));

		memcachedClient.setWithNoReply("name", 0, "dennis",
				new StringTranscoder());
		memcachedClient.appendWithNoReply("name", " zhuang");
		memcachedClient.prependWithNoReply("name", "hello ");
		assertEquals("hello dennis zhuang", memcachedClient.get("name"));

		memcachedClient.addWithNoReply("name", 0, "test",
				new StringTranscoder());
		assertEquals("hello dennis zhuang", memcachedClient.get("name"));
		memcachedClient.replaceWithNoReply("name", 0, "test",
				new StringTranscoder());
		assertEquals("test", memcachedClient.get("name"));

		memcachedClient.setWithNoReply("a", 0, 1);
		GetsResponse<Integer> getsResponse = memcachedClient.gets("a");
		memcachedClient.casWithNoReply("a", 0, getsResponse,
				new CASOperation<Integer>() {

					@Override
					public int getMaxTries() {
						return 1;
					}

					@Override
					public Integer getNewValue(long currentCAS,
							Integer currentValue) {
						return currentValue + 1;
					}

				});
		assertEquals(2, memcachedClient.get("a"));
		// repeat onece,it is not effected
		memcachedClient.casWithNoReply("a", getsResponse,
				new CASOperation<Integer>() {

					@Override
					public int getMaxTries() {
						return 1;
					}

					@Override
					public Integer getNewValue(long currentCAS,
							Integer currentValue) {
						return currentValue + 1;
					}

				});
		assertEquals(2, memcachedClient.get("a"));

		memcachedClient.casWithNoReply("a", new CASOperation<Integer>() {

			@Override
			public int getMaxTries() {
				return 1;
			}

			@Override
			public Integer getNewValue(long currentCAS, Integer currentValue) {
				return currentValue + 1;
			}

		});
		assertEquals(3, memcachedClient.get("a"));
	}

	public void testDelete() throws Exception {
		assertTrue(memcachedClient.set("name", 0, "dennis"));
		assertEquals("dennis", memcachedClient.get("name"));
		assertTrue(memcachedClient.delete("name"));
		assertNull(memcachedClient.get("name"));
		assertFalse(memcachedClient.delete("not_exists"));

		memcachedClient.set("name", 0, "dennis");
		assertEquals("dennis", memcachedClient.get("name"));
		assertTrue(memcachedClient.delete("name", 2));
		assertNull(memcachedClient.get("name"));
		// add,replace fail
		assertFalse(memcachedClient.add("name", 0, "zhuang"));
		assertFalse(memcachedClient.replace("name", 0, "zhuang"));
		Thread.sleep(3000);
		// add,replace success
		assertTrue(memcachedClient.add("name", 0, "zhuang"));
		assertTrue(memcachedClient.replace("name", 0, "zhuang"));
	}

	public void testDeleteWithNoReply() throws Exception {
		assertTrue(memcachedClient.set("name", 0, "dennis"));
		assertEquals("dennis", memcachedClient.get("name"));
		memcachedClient.deleteWithNoReply("name");
		assertNull(memcachedClient.get("name"));
		memcachedClient.deleteWithNoReply("not_exists");

		memcachedClient.set("name", 0, "dennis");
		assertEquals("dennis", memcachedClient.get("name"));
		memcachedClient.deleteWithNoReply("name", 2);
		assertNull(memcachedClient.get("name"));
		// add,replace fail
		assertFalse(memcachedClient.add("name", 0, "zhuang"));
		assertFalse(memcachedClient.replace("name", 0, "zhuang"));
		Thread.sleep(3000);
		// add,replace success
		assertTrue(memcachedClient.add("name", 0, "zhuang"));
		assertTrue(memcachedClient.replace("name", 0, "zhuang"));
	}

	//
	public void testMultiGet() throws Exception {
		for (int i = 0; i < 50; i++)
			assertTrue(memcachedClient.add(String.valueOf(i), 0, i));

		List<String> keys = new ArrayList<String>();
		for (int i = 0; i < 100; i++)
			keys.add(String.valueOf(i));

		Map<String, Integer> result = memcachedClient.get(keys, 10000);
		assertEquals(50, result.size());

		for (int i = 0; i < 50; i++)
			assertEquals((Integer) i, result.get(String.valueOf(i)));

	}

	public void testGets() throws Exception {
		memcachedClient.add("name", 0, "dennis");
		GetsResponse<String> getsResponse = memcachedClient.gets("name");
		assertEquals("dennis", getsResponse.getValue());
		long oldCas = getsResponse.getCas();
		getsResponse = memcachedClient.gets("name", 2000,
				new StringTranscoder());
		assertEquals("dennis", getsResponse.getValue());
		assertEquals(oldCas, getsResponse.getCas());

		memcachedClient.set("name", 0, "zhuang");
		getsResponse = memcachedClient.gets("name", 2000);
		assertEquals("zhuang", getsResponse.getValue());
		assertFalse(oldCas == getsResponse.getCas());

	}

	public void testVersion() throws Exception {
		assertNotNull(memcachedClient.version());
		System.out.println(memcachedClient.version());
		assertTrue(memcachedClient.getVersions(5000).size() > 0);
		System.out.println(memcachedClient.getVersions());

	}

	public void testStats() throws Exception {
		assertTrue(memcachedClient.getStats().size() > 0);
		System.out.println(memcachedClient.getStats());
		memcachedClient.set("a", 0, 1);
		assertTrue(memcachedClient.getStatsByItem("items").size() > 0);
		System.out.println(memcachedClient.getStatsByItem("items"));
	}

	public void testFlushAll() throws Exception {
		for (int i = 0; i < 50; i++)
			assertTrue(memcachedClient.add(String.valueOf(i), 0, i));
		List<String> keys = new ArrayList<String>();
		for (int i = 0; i < 100; i++)
			keys.add(String.valueOf(i));
		Map<String, Integer> result = memcachedClient.get(keys);
		assertEquals(50, result.size());
		for (int i = 0; i < 50; i++)
			assertEquals((Integer) i, result.get(String.valueOf(i)));
		memcachedClient.flushAll();
		result = memcachedClient.get(keys);
		assertTrue(result.isEmpty());
	}

	public void testSetLoggingLevelVerbosity() throws Exception {
		memcachedClient.setLoggingLevelVerbosity(AddrUtil.getAddresses(
				properties.getProperty("test.memcached.servers")).get(0), 2);
		memcachedClient.setLoggingLevelVerbosityWithNoReply(AddrUtil
				.getAddresses(properties.getProperty("test.memcached.servers"))
				.get(0), 3);
		memcachedClient.setLoggingLevelVerbosityWithNoReply(AddrUtil
				.getAddresses(properties.getProperty("test.memcached.servers"))
				.get(0), 0);
	}

	public void testFlushAllWithNoReply() throws Exception {
		for (int i = 0; i < 50; i++)
			assertTrue(memcachedClient.add(String.valueOf(i), 0, i));
		List<String> keys = new ArrayList<String>();
		for (int i = 0; i < 100; i++)
			keys.add(String.valueOf(i));
		Map<String, Integer> result = memcachedClient.get(keys);
		assertEquals(50, result.size());
		for (int i = 0; i < 50; i++)
			assertEquals((Integer) i, result.get(String.valueOf(i)));
		memcachedClient.flushAllWithNoReply();
		result = memcachedClient.get(keys, 5000);
		assertTrue(result.isEmpty());
	}

	public void testIncr() throws Exception {
		try {
			memcachedClient.incr("a", 5);
			fail();
		} catch (MemcachedException e) {
			assertEquals(
					"net.rubyeye.xmemcached.exception.MemcachedException: The key's value is not found for increase or decrease",
					e.getMessage());
		}
		assertTrue(memcachedClient.set("a", 0, "1"));
		assertEquals(6, memcachedClient.incr("a", 5));
		assertEquals(10, memcachedClient.incr("a", 4));
	}

	public void testIncrWithNoReply() throws Exception {
		memcachedClient.incrWithNoReply("a", 5);
		assertTrue(memcachedClient.set("a", 0, "1"));
		memcachedClient.incrWithNoReply("a", 5);
		assertEquals("6", memcachedClient.get("a"));
		memcachedClient.incrWithNoReply("a", 4);
		assertEquals("10", memcachedClient.get("a"));
	}

	public void testDecr() throws Exception {
		try {
			memcachedClient.decr("a", 5);
			fail();
		} catch (MemcachedException e) {
			assertEquals(
					"net.rubyeye.xmemcached.exception.MemcachedException: The key's value is not found for increase or decrease",
					e.getMessage());
		}
		assertTrue(memcachedClient.set("a", 0, "100"));
		assertEquals(50, memcachedClient.decr("a", 50));
		assertEquals(46, memcachedClient.decr("a", 4));
	}

	public void testDecrWithNoReply() throws Exception {
		memcachedClient.decrWithNoReply("a", 5);

		assertTrue(memcachedClient.set("a", 0, "100"));
		memcachedClient.decrWithNoReply("a", 50);
		assertEquals("50 ", memcachedClient.get("a"));
		memcachedClient.decrWithNoReply("a", 4);
		assertEquals("46 ", memcachedClient.get("a"));
	}

	public void testCAS() throws Exception {
		memcachedClient.add("name", 0, "dennis");
		GetsResponse<String> getsResponse = memcachedClient.gets("name");
		assertEquals("dennis", getsResponse.getValue());
		assertTrue(memcachedClient.cas("name", getsResponse,
				new CASOperation<String>() {

					@Override
					public int getMaxTries() {
						return 1;
					}

					@Override
					public String getNewValue(long currentCAS,
							String currentValue) {
						return "zhuang";
					}

				}));
		assertEquals("zhuang", memcachedClient.get("name"));
		getsResponse = memcachedClient.gets("name");
		memcachedClient.set("name", 0, "dennis");
		// cas fail
		assertFalse(memcachedClient.cas("name", 0, "zhuang", getsResponse
				.getCas()));
		assertEquals("dennis", memcachedClient.get("name"));
	}

	public void testAutoReconnect() throws Exception {
		final String key = "name";
		memcachedClient.set(key, 0, "dennis");
		assertEquals("dennis", memcachedClient.get(key));
		CountDownLatch latch = new CountDownLatch(1);
		int currentServerCount = memcachedClient.getAvaliableServers().size();
		MockErrorTextGetOneCommand errorCommand = new MockErrorTextGetOneCommand(
				key, key.getBytes(), CommandType.GET_ONE, latch);
		this.memcachedClient.getConnector().send(errorCommand);
		latch.await(MemcachedClient.DEFAULT_OP_TIMEOUT, TimeUnit.MILLISECONDS);
		assertTrue(errorCommand.isDecoded());
		// wait for reconnecting
		Thread.sleep(2000);
		assertEquals(currentServerCount, memcachedClient.getAvaliableServers()
				.size());
		// It works
		assertEquals("dennis", memcachedClient.get(key));
	}

	public void testOperationDecodeTimeOut() throws Exception {
		memcachedClient.set("name", 0, "dennis");
		assertEquals("dennis", memcachedClient.get("name"));
		CountDownLatch latch = new CountDownLatch(1);
		MockDecodeTimeoutTextGetOneCommand errorCommand = new MockDecodeTimeoutTextGetOneCommand(
				"name", "name".getBytes(), CommandType.GET_ONE, latch, 1000);
		this.memcachedClient.getConnector().send(errorCommand);
		// wait 100 milliseconds,the operation will be timeout
		latch.await(100, TimeUnit.MILLISECONDS);
		assertNull(errorCommand.getResult());
		Thread.sleep(1000);
		// It works.
		assertNotNull(errorCommand.getResult());
		assertEquals("dennis", memcachedClient.get("name"));
	}

	public void TESTOPERATIONENCODETIMEOUT() throws Exception {
		memcachedClient.set("name", 0, "dennis");
		assertEquals("dennis", memcachedClient.get("name"));
		long writeMessageCount = memcachedClient.getConnector().getStatistics()
				.getWriteMessageCount();
		CountDownLatch latch = new CountDownLatch(1);
		MockEncodeTimeoutTextGetOneCommand errorCommand = new MockEncodeTimeoutTextGetOneCommand(
				"name", "name".getBytes(), CommandType.GET_ONE, latch, 1000);
		this.memcachedClient.getConnector().send(errorCommand);
		// Force write thread to encode command
		errorCommand.setIoBuffer(null);
		// wait 100 milliseconds,the operation will be timeout
		if (!latch.await(100, TimeUnit.MILLISECONDS)) {
			errorCommand.cancel();
		}
		Thread.sleep(1000);
		// It is not written to channel,because it is canceled.
		assertEquals(writeMessageCount, memcachedClient.getConnector()
				.getStatistics().getWriteMessageCount());
		// It works
		assertEquals("dennis", memcachedClient.get("name"));
	}

	public void testRemoveAndAddServer() throws Exception {
		String servers = properties.getProperty("test.memcached.servers");
		memcachedClient.set("name", 0, "dennis");
		assertEquals("dennis", memcachedClient.get("name"));
		memcachedClient.removeServer(servers);

		synchronized (this) {
			while (memcachedClient.getAvaliableServers().size() > 0)
				wait(1000);
		}
		assertEquals(0, memcachedClient.getAvaliableServers().size());
		try {
			memcachedClient.get("name");
			fail();
		} catch (MemcachedException e) {
			assertEquals("There is no avriable session at this moment", e
					.getMessage());
		}

		memcachedClient.addServer(properties
				.getProperty("test.memcached.servers"));
		synchronized (this) {
			while (memcachedClient.getAvaliableServers().size() < AddrUtil
					.getAddresses(servers).size())
				wait(1000);
		}
		Thread.sleep(5000);
		assertEquals("dennis", memcachedClient.get("name"));
	}

	public void testWeightedServers() throws Exception {
		// shutdown current client
		this.memcachedClient.shutdown();

		MemcachedClientBuilder builder = createWeightedBuilder();
		builder.getConfiguration().setStatisticsServer(true);
		memcachedClient = builder.build();
		memcachedClient.flushAll(5000);

		Map<InetSocketAddress, Map<String, String>> oldStats = memcachedClient
				.getStats();

		for (int i = 0; i < 100; i++) {
			memcachedClient.set(String.valueOf(i), 0, i);
		}
		for (int i = 0; i < 100; i++) {
			assertEquals(i, memcachedClient.get(String.valueOf(i)));
		}

		List<InetSocketAddress> addressList = AddrUtil.getAddresses(properties
				.getProperty("test.memcached.servers"));
		Map<InetSocketAddress, Map<String, String>> newStats = memcachedClient
				.getStats();
		for (InetSocketAddress address : addressList) {
			int oldSets = Integer
					.parseInt(oldStats.get(address).get("cmd_set"));
			int newSets = Integer
					.parseInt(newStats.get(address).get("cmd_set"));
			System.out.println("sets:" + (newSets - oldSets));
			int oldGets = Integer
					.parseInt(oldStats.get(address).get("cmd_get"));
			int newGets = Integer
					.parseInt(newStats.get(address).get("cmd_get"));
			System.out.println("gets:" + (newGets - oldGets));
		}
	}

	public void testErrorCommand() throws Exception {
		Command nonexisCmd = new Command() {
			@Override
			public boolean decode(MemcachedTCPSession session, ByteBuffer buffer) {
				return decodeError(ByteUtils.nextLine(buffer));
			}

			@Override
			public void encode(BufferAllocator bufferAllocator) {
				this.ioBuffer = bufferAllocator.wrap(ByteBuffer.wrap("test\r\n"
						.getBytes()));
			}

		};
		nonexisCmd.setKey("test");
		nonexisCmd.setLatch(new CountDownLatch(1));
		this.memcachedClient.getConnector().send(nonexisCmd);
		// this.memcachedClient.flushAll();
		nonexisCmd.getLatch().await();

		assertNotNull(nonexisCmd.getException());
		assertEquals("Nonexist command,check your memcached version please.",
				nonexisCmd.getException().getMessage());
		assertTrue(nonexisCmd.getException() instanceof UnknownCommandException);

		memcachedClient.set("name", 0, "dennis");
		assertEquals("dennis", memcachedClient.get("name"));
	}

	@Override
	public void tearDown() throws Exception {
		this.memcachedClient.shutdown();
	}

}
