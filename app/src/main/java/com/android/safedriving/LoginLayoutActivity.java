package com.android.safedriving;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import com.android.safedriving.ActivityManager.BaseActivity;
import com.android.safedriving.HttpUtil.HttpUrlConstant;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import static android.text.TextUtils.isEmpty;

/**
 * LoginLayoutActivity功能：
 * 1.实现驾驶员登录的功能。
 * 2.此登录页面是启动页面。
 */
public class LoginLayoutActivity extends BaseActivity {
    private EditText accountEdit;
    private EditText passwordEdit;
    private Button loginButton;
    private CheckBox remmberPasswordCheckBox;

    private SharedPreferences pref;
    private SharedPreferences.Editor editor;

    public String getAccount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_layout);

        pref = PreferenceManager.getDefaultSharedPreferences(this);
        accountEdit = (EditText) findViewById(R.id.loginAccount_editText);
        passwordEdit = (EditText) findViewById(R.id.loginPassword_editText);
        loginButton = (Button) findViewById(R.id.login_button);
        remmberPasswordCheckBox = (CheckBox) findViewById(R.id.rememberPassword_checkBox);

        boolean isRemember = pref.getBoolean("remember_password",false);
        if(isRemember){
            String account = pref.getString("account","");
            String password = pref.getString("password","");
            accountEdit.setText(account);
            passwordEdit.setText(password);
            remmberPasswordCheckBox.setChecked(true);
        }

        /**
         * 监听EditText控件，都不为空时，登录按钮才可点击。
         */
        if(! TextUtils.isEmpty(accountEdit.getText()) && !TextUtils.isEmpty(passwordEdit.getText())){
            loginButton.setEnabled(Boolean.TRUE);
        }
        accountEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if(TextUtils.isEmpty(accountEdit.getText()) || TextUtils.isEmpty(passwordEdit.getText())){
                    loginButton.setEnabled(Boolean.FALSE);
                }else {
                    loginButton.setEnabled(Boolean.TRUE);
                }
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });

        passwordEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if(TextUtils.isEmpty(passwordEdit.getText()) || TextUtils.isEmpty(accountEdit.getText())){
                    loginButton.setEnabled(Boolean.FALSE);
                }else {
                    loginButton.setEnabled(Boolean.TRUE);
                }
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });

        /**
         * 注册login按钮的监听事件：
         * 1.当账号和密码合法时，登录成功。
         */
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                /**
                 * 获取用户输入的账号和密码。
                 */
                String account = accountEdit.getText().toString();
                String password = passwordEdit.getText().toString();
                getAccount = account;

                /**
                 * 检验账号和密码的合法性。
                 */
//                if(account.equals("test") && password.equals("test")){
//                    Intent intent = new Intent(LoginLayoutActivity.this,RealtimeAnalysisActivity.class);
//                    startActivity(intent);
//                    finish();
//                }else{
//                    Toast.makeText(LoginLayoutActivity.this,"账号或密码错误，请重新输入！",Toast.LENGTH_SHORT).show();
//                }
                if(! isEmpty(account) && !isEmpty(password)){
                    editor = pref.edit();
                    if(remmberPasswordCheckBox.isChecked()){
                        editor.putBoolean("remember_password", true);
                        editor.putString("account", account);
                        editor.putString("password", password);
                    }else{
                        editor.clear();
                    }
                    editor.apply();
//                    Toast.makeText(LoginLayoutActivity.this,"正在登录...",Toast.LENGTH_SHORT).show();
                    login(account,password);

                }
            }
        });

    }

    private void login(String account,String password){
        String loginStr = HttpUrlConstant.loginURL+"?DAccount="+account+"&DPassword="+password;
//        new MyAsyncTask(resultTV).execute(loginStr);
        new MyAsyncTask().execute(loginStr);
    }

    public class MyAsyncTask extends AsyncTask<String,Integer,String>{

//        private TextView tv;

        public MyAsyncTask(){
//            tv = v;
        }

        @Override
        protected void onPreExecute() {
            Log.d("LoginLayoutActivity","onPreExecute");
        }

        /**
         *
         * @param strings params是一个数组，是AsyncTask在激活运行时调用的execute()方法传入的参数。
         * @return
         */
        @Override
        protected String doInBackground(String... strings) {
            Log.d("LoginLayoutActivity","doInBackground");

            HttpURLConnection connection = null;
            StringBuilder response = new StringBuilder();
            try{
                URL url = new URL(strings[0]);
                connection = (HttpURLConnection)url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(80000);
                connection.setReadTimeout(80000);
                InputStream in = connection.getInputStream();

                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                String line;
                while ((line = reader.readLine() )!= null){
                    response.append(line);
                }
            }catch (MalformedURLException e){
                e.printStackTrace();
            }catch (IOException e){
                e.printStackTrace();
            }

            return response.toString();//此处的返回值作为参数传入onPostExecute()
        }

        /**
         * 本方法在UI线程中执行，典型用法是更新进度条。
         * @param values
         */
        @Override
        protected void onProgressUpdate(Integer... values) {

        }

        /**
         * 本方法在UI线程中运行，可直接操作UI元素。
         * @param s
         */
        @Override
        protected void onPostExecute(String s) {
            Log.d("LoginLayoutActivity","onPostExecute");
//            System.out.println(s);
            if(s.equals("code:200;message:登陆成功")){

                Intent intent = new Intent(LoginLayoutActivity.this,RealtimeAnalysisActivity.class);
                Bundle bundle = new Bundle();
                bundle.putString("account",getAccount);
                intent.putExtras(bundle);
                startActivity(intent);
            }else {
                Toast.makeText(LoginLayoutActivity.this,"登录失败，请输入正确的账号和密码！",Toast.LENGTH_SHORT).show();
            }
//            tv.setText(s);
        }
    }


}
