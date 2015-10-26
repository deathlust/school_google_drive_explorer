package ru.spb.deathlust.googledriveexplorer;

import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.MetadataBuffer;

import java.security.InvalidParameterException;

public class MainActivity extends AppCompatActivity
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, ResultCallback<DriveApi.MetadataBufferResult> {
    /**
     * Request code for auto Google Play Services error resolution.
     */
    private static final int REQUEST_CODE_RESOLUTION = 1;
//    public static final String EXISTING_FOLDER_ID = "0B2EEtIjPUdX6MERsWlYxN3J6RU0";
//    private static DriveId sFolderId = DriveId.decodeFromString(EXISTING_FOLDER_ID);

    private static final String EXTRA_NAME = "name";
    private GoogleApiClient mGoogleApiClient;
    private DriveId mId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Drive.API)
                .addScope(Drive.SCOPE_FILE)
                .addScope(Drive.SCOPE_APPFOLDER)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        final Intent intent = getIntent();
        if (intent.hasExtra(EXTRA_NAME)) {
            mId = intent.getParcelableExtra(EXTRA_NAME);
        } else {
            mId = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    @Override
    public void onConnected(Bundle bundle) {
        final DriveFolder folder;
        if (mId != null) {
            folder = Drive.DriveApi.getFolder(mGoogleApiClient, mId);
        } else {
            folder = Drive.DriveApi.getRootFolder(mGoogleApiClient);
        }
        folder.listChildren(mGoogleApiClient).setResultCallback(this);
    }

    @Override
    public void onResult(DriveApi.MetadataBufferResult result) {
        if (!result.getStatus().isSuccess()) {
            return;
        }
        final RecyclerView view = (RecyclerView)findViewById(R.id.folderExplorer);
        view.setAdapter(new FolderListAdapter(this, result.getMetadataBuffer()));
    }

    @Override
    public void onConnectionSuspended(int i) {
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        if (!result.hasResolution()) {
            GoogleApiAvailability.getInstance().getErrorDialog(this, result.getErrorCode(), 0).show();
            return;
        }
        try {
            result.startResolutionForResult(this, REQUEST_CODE_RESOLUTION);
        } catch (IntentSender.SendIntentException e) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_RESOLUTION && resultCode == RESULT_OK) {
            mGoogleApiClient.connect();
        }
    }

    private static class FolderListAdapter extends RecyclerView.Adapter<FolderListAdapter.ViewHolder> {
        private MetadataBuffer mBuffer;
        private int mBackground;

        public FolderListAdapter(Context context, MetadataBuffer buffer) {
            mBuffer = buffer;
            TypedValue typedValue = new TypedValue();
            context.getTheme().resolveAttribute(R.attr.selectableItemBackground, typedValue, true);
            mBackground = typedValue.resourceId;
        }

        @Override
        public int getItemViewType(int position) {
            return mBuffer.get(position).isFolder() ? R.integer.item_folder : R.integer.item_file;
        }

        @Override
        public ViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
            final View view = //new TextView(parent.getContext());
            //view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            //        ViewGroup.LayoutParams.WRAP_CONTENT));
            //view.setTextAppearance(R.attr.textAppearanceListItem);
                    LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item, parent, false);
            view.setBackgroundResource(mBackground);
            switch (viewType) {
                case R.integer.item_file:
                    return new ViewHolder.File(view);
                case R.integer.item_folder:
                    return new ViewHolder.Folder(view);
                default:
                    throw new InvalidParameterException("Wrong object type");
            }
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, final int position) {
            final TextView view = holder.mTextView;
            view.setText(mBuffer.get(position).getTitle());

            if (holder instanceof ViewHolder.Folder) {
                view.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final Context context = v.getContext();
                        final Intent intent = new Intent(context, MainActivity.class);
                        intent.putExtra(MainActivity.EXTRA_NAME, mBuffer.get(position).getDriveId());
                        context.startActivity(intent);
                    }
                });
                view.setClickable(true);
            } else {
                view.setClickable(false);
            }
        }

        @Override
        public int getItemCount() {
            return mBuffer.getCount();
        }

        public static abstract class ViewHolder extends RecyclerView.ViewHolder {
            public final TextView mTextView;

            protected ViewHolder(View itemView, int drawable) {
                super(itemView);
                mTextView = (TextView) itemView;
                final Drawable img = itemView.getContext().getResources().getDrawable(drawable);
                img.setBounds(0, 0, 32, 32);
                mTextView.setCompoundDrawables(img, null, null, null);
            }
            public static class Folder extends ViewHolder {
                public Folder(View itemView) {
                    super(itemView, R.drawable.folder);
                }
            }
            public static class File extends ViewHolder {
                public File(View itemView) {
                    super(itemView, R.drawable.file);
                }
            }
        }
    }
}
