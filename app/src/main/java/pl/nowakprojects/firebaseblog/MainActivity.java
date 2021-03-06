package pl.nowakprojects.firebaseblog;

import android.app.ProgressDialog;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MainActivity extends AppCompatActivity {

    private DatabaseReference mBlogPostsDatabase;
    private DatabaseReference mUsersDatabase;
    private DatabaseReference mLikeDatabase;
    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener;

    private ProgressDialog mProgressDialog;

    private RecyclerView mBlogList;

    private boolean mProcessLike = false;

    @Override
    protected void onStart() {
        super.onStart();

        checkIfUserExistsInDatabase();

        setupRecyclerView();
        mAuth.addAuthStateListener(mAuthStateListener);
    }

    private void setupRecyclerView() {
        FirebaseRecyclerAdapter<Blog,BlogViewHolder> firebaseRecyclerAdapter =
                new FirebaseRecyclerAdapter<Blog, BlogViewHolder>(
                       Blog.class, R.layout.blog_row,BlogViewHolder.class, mBlogPostsDatabase
                ) {
            @Override
            protected void populateViewHolder(BlogViewHolder viewHolder, Blog model, int position) {
                final String postKey = getRef(position).getKey();

                viewHolder.setTitle(model.getTitle());
                viewHolder.setDesc(model.getDesc());
                viewHolder.setImage(MainActivity.this, model.getImage());
                viewHolder.setUsername(model.getUsername());
                viewHolder.setLikeButton(postKey);

                viewHolder.getView().setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent singleBlogIntent = new Intent(MainActivity.this,SingleBlogActivity.class);
                        singleBlogIntent.putExtra("BLOG_ID",postKey);
                        startActivity(singleBlogIntent);
                    }
                });

                viewHolder.getLikeButton().setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        mProcessLike=true;

                        likePost(postKey);
                    }
                });
            }
        };

        mBlogList.setAdapter(firebaseRecyclerAdapter);
    }

    private void likePost(final String postKey) {
        mLikeDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(mProcessLike) {
                    if (checkIfUserAlreadyLikeThisPost(dataSnapshot, postKey))
                        mLikeDatabase.child(postKey).child(mAuth.getCurrentUser().getUid()).removeValue();
                    else
                        mLikeDatabase.child(postKey).child(mAuth.getCurrentUser().getUid()).setValue("likes this post!");
                }

                mProcessLike = false;

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    private boolean checkIfUserAlreadyLikeThisPost(DataSnapshot dataSnapshot, String postKey) {
        return dataSnapshot.child(postKey).hasChild(mAuth.getCurrentUser().getUid());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initFirebase();
        initUserInterface();

        //checkIfUserExistsInDatabase();
    }

    private void initFirebase() {
        mBlogPostsDatabase = FirebaseDatabase.getInstance().getReference().child("FirebaseBlog");
        mBlogPostsDatabase.keepSynced(true);
        mUsersDatabase = FirebaseDatabase.getInstance().getReference().child("Users");
        mUsersDatabase.keepSynced(true);
        mLikeDatabase = FirebaseDatabase.getInstance().getReference().child("Likes");

        mAuth = FirebaseAuth.getInstance();
        mAuthStateListener = new FirebaseAuth.AuthStateListener() {

            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                if (!userIsLogged()) {
                    Intent loginIntent = new Intent(MainActivity.this, LoginActivity.class);
                    loginIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP); //uzytkownik nie moze wrócić do poprzedniej
                    startActivity(loginIntent);
                }
            }

            private boolean userIsLogged(){
                return mAuth.getCurrentUser() != null;
            }
        };
    }

    private void initUserInterface() {
        mBlogList = (RecyclerView) findViewById(R.id.blog_list);
        mBlogList.setHasFixedSize(true);//dodawania, usuwanie elementów nie zmienia jej rozmiaru
        mBlogList.setLayoutManager(new LinearLayoutManager(this));
    }

    private void checkIfUserExistsInDatabase() {
        if(mAuth.getCurrentUser()!=null) {
            final String userId = mAuth.getCurrentUser().getUid();

            mUsersDatabase.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if (!dataSnapshot.hasChild(userId))
                        startAccountSetupActivity();
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            });
        }
    }

    private void startAccountSetupActivity() {
        Intent setupIntent = new Intent(MainActivity.this, SetupActivity.class);
        setupIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP); //uzytkownik nie moze wrócić do poprzedniej
        startActivity(setupIntent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.main_menu,menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch(item.getItemId()){
            case R.id.action_add:
                startActivity(new Intent(getApplicationContext(),PostActivity.class));break;
            case R.id.action_logout:
                logout();break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void logout() {
        mAuth.signOut();
    }
}
