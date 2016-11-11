package jp.realglobe.sugo.actor.android.call;

import android.app.Activity;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.view.WindowManager;

/**
 * 設定画面を表示する
 * Created by fukuchidaisuke on 16/11/10.
 */

public class SettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
        fragmentTransaction.replace(android.R.id.content, new SettingsPreferenceFragment());
        fragmentTransaction.commit();
    }

}
