package jp.realglobe.sugo.actor.android.call;

import android.util.Log;

import org.json.JSONObject;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import io.socket.client.Ack;
import io.socket.client.Manager;
import io.socket.client.Socket;
import jp.realglobe.sg.socket.Constants;

/**
 * sugos の Actor
 * Created by fukuchidaisuke on 16/11/10.
 */
final class Actor {

    private static final String LOG_TAG = Actor.class.getName();

    private static final String NAMESPACE = "/actors";

    // sugos 送信データのキー
    private static final String KEY_KEY = "key";
    private static final String KEY_NAME = "name";
    private static final String KEY_SPEC = "spec";
    private static final String KEY_VERSION = "version";
    private static final String KEY_DESC = "desc";
    private static final String KEY_METHODS = "methods";
    private static final String KEY_MODULE = "module";
    private static final String KEY_EVENT = "event";
    private static final String KEY_DATA = "data";

    private final String server;
    private final String key;
    private final String name;
    private final String version;
    private final String description;
    private final String module;

    private Runnable onConnection;

    private Socket socket;

    private boolean greeted;

    Actor(String server, String key, String name, String version, String description, String module) {
        this.server = server;
        this.key = key;
        this.name = name;
        this.version = version;
        this.description = description;
        this.module = module;
    }

    synchronized boolean isConnecting() {
        return this.socket != null;
    }

    /**
     * つなぐ
     */
    synchronized void connect() {
        if (this.socket != null) {
            Log.i(LOG_TAG, "Already connected");
            return;
        }
        this.socket = (new Manager(URI.create(this.server))).socket(NAMESPACE);

        this.socket.on(Socket.EVENT_CONNECT, args -> {
            Log.d(LOG_TAG, "Connected to " + server);
            processAfterConnection();
        });
        this.socket.on(Socket.EVENT_DISCONNECT, args -> Log.d(LOG_TAG, "Disconnected from " + server));
        this.socket.connect();
    }

    private synchronized void processAfterConnection() {
        if (this.socket == null) {
            // 終了
            return;
        }

        final Map<String, Object> data = new HashMap<>();
        data.put(KEY_KEY, this.key);
        this.socket.emit(Constants.GreetingEvents.HI, new JSONObject(data), (Ack) args -> {
            Log.d(LOG_TAG, socket.id() + " greeted");
            processAfterGreeting();
        });
    }

    private synchronized void processAfterGreeting() {
        if (this.socket == null) {
            // 終了
            return;
        }
        this.greeted = true;

        final Map<String, Object> specData = new HashMap<>();
        specData.put(KEY_NAME, this.name);
        specData.put(KEY_VERSION, this.version);
        if (this.description != null) {
            specData.put(KEY_DESC, this.description);
        }
        specData.put(KEY_METHODS, new HashMap<String, Object>());

        final Map<String, Object> data = new HashMap<>();
        data.put(KEY_NAME, this.module);
        data.put(KEY_SPEC, specData);

        socket.emit(Constants.RemoteEvents.SPEC, new JSONObject(data), (Ack) args -> {
            Log.d(LOG_TAG, socket.id() + " sent specification");
            if (this.onConnection != null) {
                this.onConnection.run();
            }
        });
    }

    /**
     * つながったときに実行する関数を登録する
     *
     * @param onConnection つながったときに実行する関数
     */
    synchronized void setOnConnection(Runnable onConnection) {
        this.onConnection = onConnection;
    }

    /**
     * 送る
     *
     * @param event イベント
     * @param data  データ
     */
    synchronized void emit(String event, Map<String, Object> data) {
        final Map<String, Object> wrapData = new HashMap<>();
        wrapData.put(KEY_KEY, this.key);
        wrapData.put(KEY_MODULE, this.module);
        wrapData.put(KEY_EVENT, event);
        if (data != null) {
            wrapData.put(KEY_DATA, data);
        }
        socket.emit(Constants.RemoteEvents.PIPE, new JSONObject(wrapData));
    }

    /**
     * 切断する
     */
    synchronized void disconnect() {
        if (this.socket == null) {
            Log.i(LOG_TAG, "Not connecting");
            return;
        }
        final Socket socket = this.socket;
        this.socket = null;

        if (!this.greeted) {
            socket.disconnect();
            return;
        }
        this.greeted = false;

        final Map<String, Object> data = new HashMap<>();
        data.put(KEY_KEY, this.key);
        socket.emit(Constants.GreetingEvents.BYE, new JSONObject(data), (Ack) args -> socket.disconnect());
    }

}
