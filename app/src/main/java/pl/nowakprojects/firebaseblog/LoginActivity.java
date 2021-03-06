package pl.nowakprojects.firebaseblog;

import android.app.ProgressDialog;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class LoginActivity extends AppCompatActivity {

    private static final int RC_SIGN_IN = 3;

    private FirebaseAuth mAuth;
    private DatabaseReference mUsersDatabase;
    private GoogleApiClient mGoogleApiClient;

    private ProgressDialog mProgressDialog;

    private EditText mLoginEmailField;
    private EditText mPasswordEmailField;

    private Button mLoginButton;
    private Button mNewAccountButton;

    private SignInButton mGoogleButton;

    private static final String TAG = "LoginActivity";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        initFirebase();
        initUserInterface();
        cofigureGoogleSignIn();
    }

    private void cofigureGoogleSignIn() {
        // Configure Google Sign In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this,
                        new GoogleApiClient.OnConnectionFailedListener() {
                            @Override
                            public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
                                Toast.makeText(getApplicationContext(),"Something with Google SignIn went wrong!",Toast.LENGTH_LONG).show();
                            }
                        })
                .addApi(Auth.GOOGLE_SIGN_IN_API,gso)
                .build();
    }

    private void signIn() {
        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);

            mProgressDialog.setMessage("Starting Sign in...");
            mProgressDialog.show();

            if (result.isSuccess()) {
                // Google Sign In was successful, authenticate with Firebase
                GoogleSignInAccount account = result.getSignInAccount();
                firebaseAuthWithGoogle(account);
            } else {
                // Google Sign In failed, update UI appropriately
                // ...
                mProgressDialog.dismiss();
            }
        }
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount acct) {
        Log.d(TAG, "firebaseAuthWithGoogle:" + acct.getId());

        AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        Log.d(TAG, "signInWithCredential:onComplete:" + task.isSuccessful());

                        // If sign in fails, display a message to the user. If sign in succeeds
                        // the auth state listener will be notified and logic to handle the
                        // signed in user can be handled in the listener.
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "signInWithCredential", task.getException());
                            Toast.makeText(LoginActivity.this, "Authentication failed.",
                                    Toast.LENGTH_SHORT).show();
                        }else{
                            checkIfUserExistsInDatabase();
                        }
                        mProgressDialog.dismiss();
                    }
                });
    }

    private void initFirebase() {
        mAuth = FirebaseAuth.getInstance();
        mUsersDatabase = FirebaseDatabase.getInstance().getReference().child("Users");
        mUsersDatabase.keepSynced(true);
    }

    private void initUserInterface() {
        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setMessage("Checking login...");

        mLoginEmailField = (EditText) findViewById(R.id.loginEmailField);
        mPasswordEmailField = (EditText) findViewById(R.id.loginPasswordField);
        
        mLoginButton = (Button) findViewById(R.id.loginButton);
        mLoginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkLogin();
            }
        });
        mGoogleButton = (SignInButton) findViewById(R.id.googleButton);
        mGoogleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                signIn();
            }
        });

        mNewAccountButton = (Button) findViewById(R.id.registerButton);
        mNewAccountButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(LoginActivity.this,RegisterActivity.class));
            }
        });

    }

    private void checkLogin() {
        String email = mLoginEmailField.getText().toString().trim();
        String password = mPasswordEmailField.getText().toString().trim();

        if(isLoginFormCompleted(email,password)){
            mProgressDialog.show();
            mAuth.signInWithEmailAndPassword(email,password).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                @Override
                public void onComplete(@NonNull Task<AuthResult> task) {
                    if(task.isSuccessful()){
                        mProgressDialog.dismiss();
                        checkIfUserExistsInDatabase();
                    }else{
                        mProgressDialog.dismiss();
                        Toast.makeText(LoginActivity.this,"Error Login!",Toast.LENGTH_LONG).show();
                    }

                }
            });
        }
    }

    private void checkIfUserExistsInDatabase() {
        if (mAuth.getCurrentUser() != null) {
            final String userId = mAuth.getCurrentUser().getUid();

            mUsersDatabase.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if (dataSnapshot.hasChild(userId))
                        startMainActivity();
                    else
                        startAccountSetupActivity();
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            });
        }
    }

    private void startAccountSetupActivity() {
        Intent setupIntent = new Intent(LoginActivity.this, SetupActivity.class);
        setupIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP); //uzytkownik nie moze wrócić do poprzedniej
        startActivity(setupIntent);
    }

    private void startMainActivity() {
        Intent mainIntent = new Intent(LoginActivity.this, MainActivity.class);
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP); //uzytkownik nie moze wrócić do poprzedniej
        startActivity(mainIntent);
    }

    private boolean isLoginFormCompleted(String email, String password) {
        return !TextUtils.isEmpty(email) && !TextUtils.isEmpty(password);
    }


}
