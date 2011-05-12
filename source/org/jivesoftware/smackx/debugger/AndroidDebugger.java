package org.jivesoftware.smackx.debugger;

import org.jivesoftware.smack.debugger.SmackDebugger;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.Connection;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.util.*;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Very simple debugger that prints to the android log the sent and received stanzas. Use
 * this debugger with caution since printing to the console is an expensive operation that may
 * even block the thread since only one thread may print at a time.<p>
 * <p/>
 * It is possible to not only print the raw sent and received stanzas but also the interpreted
 * packets by Smack. By default interpreted packets won't be printed. To enable this feature
 * just change the <tt>printInterpreted</tt> static variable to <tt>true</tt>.
 *
 * @author Gaston Dombiak
 */
public class AndroidDebugger implements SmackDebugger {

    public static boolean printInterpreted = false;
    private SimpleDateFormat dateFormatter = new SimpleDateFormat("hh:mm:ss aaa");

    private Connection connection = null;

    private PacketListener listener = null;
    private ConnectionListener connListener = null;

    private ObservableWriter writer;
    private ObservableReader reader;
    private ReaderListener readerListener;
    private WriterListener writerListener;

    public AndroidDebugger(Connection connection, ObservableWriter writer, ObservableReader reader) {
        this.connection = connection;
        this.writer = writer;
        this.reader = reader;
        createDebug();
    }

    /**
     * Creates the listeners that will print in the console when new activity is detected.
     */
    private void createDebug() {
        // Create a special Reader that wraps the main Reader and logs data to the GUI.
        readerListener = new ReaderListener() {
            public void read(String str) {
            	Log.d("SMACK",
                        dateFormatter.format(new Date()) + " RCV  (" + connection.hashCode() +
                        "): " +
                        str);
            }
        };
        reader.addReaderListener(readerListener);

        // Create a special Writer that wraps the main Writer and logs data to the GUI.
        writerListener = new WriterListener() {
            public void write(String str) {
            	Log.d("SMACK",
                        dateFormatter.format(new Date()) + " SENT (" + connection.hashCode() +
                        "): " +
                        str);
            }
        };
        writer.addWriterListener(writerListener);

        // Create a thread that will listen for all incoming packets and write them to
        // the GUI. This is what we call "interpreted" packet data, since it's the packet
        // data as Smack sees it and not as it's coming in as raw XML.
        listener = new PacketListener() {
            public void processPacket(Packet packet) {
                if (printInterpreted) {
                	Log.d("SMACK",
                            dateFormatter.format(new Date()) + " RCV PKT (" +
                            connection.hashCode() +
                            "): " +
                            packet.toXML());
                }
            }
        };

        connListener = new ConnectionListener() {
            public void connectionClosed() {
                Log.d("SMACK",
                        dateFormatter.format(new Date()) + " Connection closed (" +
                        connection.hashCode() +
                        ")");
            }

            public void connectionClosedOnError(Exception e) {
                Log.d("SMACK",
                        dateFormatter.format(new Date()) +
                        " Connection closed due to an exception (" +
                        connection.hashCode() +
                        ")");
                e.printStackTrace();
            }
            public void reconnectionFailed(Exception e) {
                Log.d("SMACK",
                        dateFormatter.format(new Date()) +
                        " Reconnection failed due to an exception (" +
                        connection.hashCode() +
                        ")");
                e.printStackTrace();
            }
            public void reconnectionSuccessful() {
                Log.d("SMACK",
                        dateFormatter.format(new Date()) + " Connection reconnected (" +
                        connection.hashCode() +
                        ")");
            }
            public void reconnectingIn(int seconds) {
                Log.d("SMACK",
                        dateFormatter.format(new Date()) + " Connection (" +
                        connection.hashCode() +
                        ") will reconnect in " + seconds);
            }
        };
    }

    public void userHasLogged(String user) {
        boolean isAnonymous = "".equals(StringUtils.parseName(user));
        String title =
                "User logged (" + connection.hashCode() + "): "
                + (isAnonymous ? "" : StringUtils.parseBareAddress(user))
                + "@"
                + connection.getServiceName()
                + ":"
                + connection.getPort();
        title += "/" + StringUtils.parseResource(user);
        Log.d("SMACK", title);
        // Add the connection listener to the connection so that the debugger can be notified
        // whenever the connection is closed.
        connection.addConnectionListener(connListener);
    }

    public PacketListener getReaderListener() {
        return listener;
    }

    public PacketListener getWriterListener() {
        return null;
    }
}
