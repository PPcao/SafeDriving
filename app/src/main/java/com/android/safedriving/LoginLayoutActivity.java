package com.android.safedriving;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

/**
 * LoginLayoutActivity功能：
 * 1.实现驾驶员登录的功能。
 * 2.此登录页面是启动页面。
 */
public class LoginLayoutActivity extends AppCompatActivity {
    private EditText accountEdit;
    private EditText passwordEdit;
    private Button login;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_layout);

        accountEdit = (EditText) findViewById(R.id.loginAccount_editText);
        passwordEdit = (EditText) findViewById(R.id.loginPassword_editText);
        login = (Button) findViewById(R.id.login_button);

        /**
         * 注册login按钮的监听事件：
         * 1.当账号和密码合法时，登录成功。
         */
        login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                /**
                 * 获取用户输入的账号和密码。
                 */
                String account = accountEdit.getText().toString();
                String password = passwordEdit.getText().toString();

                /**
                 * 检验账号和密码的合法性。
                 */
                if(account.equals("test") && password.equals("test")){
                    Intent intent = new Intent(LoginLayoutActivity.this,RealtimeAnalysisActivity.class);
                    startActivity(intent);
                    finish();
                }else{
                    Toast.makeText(LoginLayoutActivity.this,"账号或密码错误，请重新输入！",Toast.LENGTH_SHORT).show();
                }

            }
        });
    }
}
