package jp.realglobe.sugo.actor.android.call;

/**
 * sugos の定数
 * Created by fukuchidaisuke on 16/11/10.
 */
final class SocketConstants {

    private SocketConstants() {
    }

    static final class GreetingEvents {
        private GreetingEvents() {
        }

        static final String HI = "sg:greet:hi";
        static final String BYE = "sg:greet:bye";
    }

    static final class RemoteEvents {

        private RemoteEvents() {
        }

        static final String SPEC = "sg:remote:spec";
        static final String PIPE = "sg:remote:pipe";
    }

}
