package net.rubyeye.xmemcached.test.unittest.commands.text;

import java.nio.ByteBuffer;

import net.rubyeye.xmemcached.command.Command;

public class TextDeleteCommandUnitTest extends BaseTextCommandUnitTest {
	public void testEncode() {
		Command command = this.commandFactory.createDeleteCommand("test",
				"test".getBytes(), 10);
		assertNull(command.getIoBuffer());
		command.encode(bufferAllocator);
		checkByteBufferEquals(command, "delete test 10\r\n");
	}

	public void testDecode() {
		Command command = this.commandFactory.createDeleteCommand("test",
				"test".getBytes(), 10);
		checkDecodeNullAndNotLineByteBuffer(command);
		checkDecodeInvalidLine(command, "STORED\r\n");
		checkDecodeInvalidLine(command, "NOT_STORED\r\n");
		checkDecodeInvalidLine(command, "END\r\n");
		checkDecodeValidLine(command, "NOT_FOUND\r\n");
		assertFalse((Boolean) command.getResult());
		checkDecodeValidLine(command, "DELETED\r\n");
		assertTrue((Boolean) command.getResult());
	}
}
