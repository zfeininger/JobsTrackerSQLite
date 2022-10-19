package edu.uga.cs.jobstrackersqlite;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;


public class ReviewJobLeadsFragment extends Fragment
        implements AddJobLeadDialogFragment.AddJobLeadDialogListener {

    private static final String TAG = "ReviewJobLeadsFragment";

    private JobLeadsData jobLeadsData = null;
    private List<JobLead> jobLeadsList;

    private RecyclerView recyclerView;
    private JobLeadRecyclerAdapter recyclerAdapter;

    public ReviewJobLeadsFragment() {
        // Required empty public constructor
    }

    public static ReviewJobLeadsFragment newInstance() {
        ReviewJobLeadsFragment fragment = new ReviewJobLeadsFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Enable the search menu population.
        // When the parameter of this method is true, Android will call onCreateOptionsMenu on
        // this fragment, when the options menu is being built for the hosting activity.
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_review_job_leads, container, false);
    }

    @Override
    public void onViewCreated( View view, Bundle savedInstanceState ) {
        super.onViewCreated(view,savedInstanceState);

        recyclerView = getView().findViewById( R.id.recyclerView );
        FloatingActionButton floatingButton = getView().findViewById(R.id.floatingActionButton);

        floatingButton.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AddJobLeadDialogFragment newFragment = new AddJobLeadDialogFragment();
                newFragment.setHostFragment( ReviewJobLeadsFragment.this );
                newFragment.show( getParentFragmentManager(), null );
            }
        });

        // use a linear layout manager for the recycler view
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager( getActivity() );
        recyclerView.setLayoutManager( layoutManager );

        jobLeadsList = new ArrayList<JobLead>();

        // Create a JobLeadsData instance, since we will need to save a new JobLead to the dn.
        // Note that even though more activites may create their own instances of the JobLeadsData
        // class, we will be using a single instance of the JobLeadsDBHelper object, since
        // that class is a singleton class.
        jobLeadsData = new JobLeadsData( getActivity() );

        // Open that database for reading of the full list of job leads.
        // Note that onResume() hasn't been called yet, so the db open in it
        // was not called yet!
        jobLeadsData.open();

        // Execute the retrieval of the job leads in an asynchronous way,
        // without blocking the main UI thread.
        new JobLeadDBReader().execute();

    }

    // This is an AsyncTask class (it extends AsyncTask) to perform DB reading of job leads, asynchronously.
    private class JobLeadDBReader extends AsyncTask<Void, List<JobLead>> {
        // This method will run as a background process to read from db.
        // It returns a list of retrieved JobLead objects.
        // It will be automatically invoked by Android, when we call the execute method
        // in the onCreate callback (the job leads review activity is started).
        @Override
        protected List<JobLead> doInBackground( Void... params ) {
            List<JobLead> jobLeadsList = jobLeadsData.retrieveAllJobLeads();

            Log.d( TAG, "JobLeadDBReader: Job leads retrieved: " + jobLeadsList.size() );

            return jobLeadsList;
        }

        // This method will be automatically called by Android once the db reading
        // background process is finished.  It will then create and set an adapter to provide
        // values for the RecyclerView.
        // onPostExecute is like the notify method in an asynchronous method call discussed in class.
        @Override
        protected void onPostExecute( List<JobLead> jobsList ) {
            //recyclerAdapter = new JobLeadRecyclerAdapter( jList );
            //recyclerView.setAdapter( recyclerAdapter );
            Log.d( TAG, "JobLeadDBReader: jList.size(): " + jobsList.size() );
            jobLeadsList.addAll( jobsList );

            // create the RecyclerAdapter and set it for the RecyclerView
            recyclerAdapter = new JobLeadRecyclerAdapter( getActivity(), jobLeadsList );
            recyclerView.setAdapter( recyclerAdapter );
        }
    }

    // This is an AsyncTask class (it extends AsyncTask) to perform DB writing of a job lead, asynchronously.
    public class JobLeadDBWriter extends AsyncTask<JobLead, JobLead> {

        // This method will run as a background process to write into db.
        // It will be automatically invoked by Android, when we call the execute method
        // in the onClick listener of the Save button.
        @Override
        protected JobLead doInBackground( JobLead... jobLeads ) {
            jobLeadsData.storeJobLead( jobLeads[0] );
            return jobLeads[0];
        }

        // This method will be automatically called by Android once the writing to the database
        // in a background process has finished.  Note that doInBackground returns a JobLead object.
        // That object will be passed as argument to onPostExecute.
        // onPostExecute is like the notify method in an asynchronous method call discussed in class.
        @Override
        protected void onPostExecute( JobLead jobLead ) {
            // Update the recycler view to include the new job lead
            jobLeadsList.add( jobLead );
            //reset the originalValues in the recyler adapter to the new list (JoLeadsList)
            recyclerAdapter.sync();
            // notify the adapter that an item has been inserted
            recyclerAdapter.notifyItemInserted(jobLeadsList.size() - 1);
            // reposition the view to show to newly added item
            recyclerView.getLayoutManager().scrollToPosition( jobLeadsList.size() - 1 );

            Log.d( TAG, "Job lead saved: " + jobLead );
        }
    }

    // this is a callback for the AddJobLeadDialogFragment which saves a new job lead
    // this method is called from the AddJobLeadDialogFragment on the "OK" button tap.
    @Override
    public void saveNewJobLead(JobLead jobLead) {
        // add the new job lead
        new JobLeadDBWriter().execute( jobLead );

        // Reposition the RecyclerView to show the JobLead most recently added (as the last item on the list).
        // Use of the post method is needed to wait until the RecyclerView is rendered, and only then
        // reposition the item into view (show the last item on the list).
        // the post method adds the argument (Runnable) to the message queue to be executed
        // by Android on the main UI thread.  It will be done *after* the setAdapter call
        // updates the list items, so the repositioning to the last item will take place
        // on the complete list of items.
        recyclerView.post( new Runnable() {
            @Override
            public void run() {
                recyclerView.smoothScrollToPosition( jobLeadsList.size()-1 );
            }
        } );
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.search_menu, menu);
        MenuItem mSearch = menu.findItem(R.id.appSearchBar);
        SearchView mSearchView = (SearchView) mSearch.getActionView();
        mSearchView.setQueryHint("Search words");
        EditText searchEditText = (EditText) mSearchView.findViewById(androidx.appcompat.R.id.search_src_text );
        searchEditText.setBackgroundColor( getResources().getColor( R.color.white ) );
        searchEditText.setTextColor(getResources().getColor(R.color.colorPrimaryDark));
        searchEditText.setHintTextColor(getResources().getColor(R.color.colorPrimary));
        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                Log.d( TAG, "Query submitted" );
                return false;
            }
            @Override
            public boolean onQueryTextChange(String newText) {
                recyclerAdapter.getFilter().filter(newText);
                return true;
            }
        });
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onResume() {
        Log.d( TAG, "ReviewJobLeadsFragment.onResume()" );
        super.onResume();
        Log.d( TAG, "ReviewJobLeadsFragment.onPause(): opening DB" );
        // open the database in onResume
        if( jobLeadsData != null && !jobLeadsData.isDBOpen() )
            jobLeadsData.open();
        ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle( getResources().getString( R.string.app_name ) );
    }

    // We need to save job leads into a file as the activity stops being a foreground activity
    @Override
    public void onPause() {
        Log.d( TAG, "ReviewJobLeadsFragment.onPause()" );
        super.onPause();

        Log.d( TAG, "ReviewJobLeadsFragment.onPause(): closing DB" );
        // close the database in onPause
        if( jobLeadsData != null )
            jobLeadsData.close();
    }
}