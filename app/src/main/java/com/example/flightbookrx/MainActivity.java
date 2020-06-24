package com.example.flightbookrx;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import com.example.flightbookrx.Adapters.TicketsAdapter;
import com.example.flightbookrx.model.ApiClient;
import com.example.flightbookrx.model.ApiService;
import com.example.flightbookrx.model.Price;
import com.example.flightbookrx.model.Ticket;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Scheduler;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;
import io.reactivex.internal.operators.flowable.FlowableFlatMap;
import io.reactivex.observables.ConnectableObservable;
import io.reactivex.observers.DisposableObserver;
import io.reactivex.schedulers.Schedulers;


    /*   CompositeDisposable is used to dispose the subscriptions in onDestroy() method.
     *  getTickets() makes an HTTP call to fetch the list of tickets.
     *  getPriceObservable() makes an HTTP call to get the price and number of tickets on each flight.
     *  You can notice replay() operator (getTickets(from, to).replay()) is used to make
        an Observable emits the data on new subscriptions without re-executing the logic again.
        In our case, the list of tickets will be emitted without making the HTTP call again.
        Without the replay method, you can notice the fetch tickets HTTP call get executed multiple times.
     *  In the first subscription, the list of tickets directly added to Adapter class and
        the RecyclerView is rendered directly without price and number of seats.
        In the second subscription, flatMap() is used to convert list of tickets to individual ticket emissions.

     *   Once the price and seats information is received, the particular row item is updated in RecyclerView.
     *     If you observe getPriceObservable(), the API call fetches Price model.
          But the map() operator is used to convert the return type from Price to Ticket.
     *    Calling ticketsObservable.connect() will start executing the Observable

*/



public class MainActivity extends AppCompatActivity implements TicketsAdapter.TicketsAdapterListener {

    @BindView(R.id.recycler_view)
    RecyclerView recycler_view;

    @BindView(R.id.toolBar)
    Toolbar toolbar;





    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String from = "DEL";
    private static final String to = "HYD";

    CompositeDisposable disposable=new CompositeDisposable();

    private Unbinder unbinder;

    private ApiService apiService;
    private TicketsAdapter mAdapter;
    private ArrayList<Ticket> ticketsList = new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
       unbinder=ButterKnife.bind(this);


        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(from + " > " + to);
        apiService = ApiClient.getClient().create(ApiService.class);
        mAdapter= new TicketsAdapter(this,ticketsList,this);


        RecyclerView.LayoutManager layoutManager=new GridLayoutManager(this,1);

        recycler_view.setLayoutManager(layoutManager);
        recycler_view.addItemDecoration(new MainActivity.GridSpacingItemDecoration(1, dpToPx(5), true));
        recycler_view.setItemAnimator(new DefaultItemAnimator());
        recycler_view.setAdapter(mAdapter);


        ConnectableObservable<List<Ticket>>ticketsObservable=getTickets(from,to).replay();


        // Calling connect to start emission
        /**
         * Fetching all tickets first
         * Observable emits List<Ticket> at once
         * All the items will be added to RecyclerView
         * */
        disposable.add(
                ticketsObservable
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeWith(new DisposableObserver<List<Ticket>>() {

                            @Override
                            public void onNext(List<Ticket> tickets) {
                                // Refreshing list
                                ticketsList.clear();
                                ticketsList.addAll(tickets);
                                mAdapter.notifyDataSetChanged();
                            }

                            @Override
                            public void onError(Throwable e) {
                                showError(e);
                            }

                            @Override
                            public void onComplete() {

                            }
                        }));

        /**
         * Fetching individual ticket price
         * First FlatMap converts single List<Ticket> to multiple emissions
         * Second FlatMap makes HTTP call on each Ticket emission
         * */
        disposable.add(
                ticketsObservable
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        /**
                         * Converting List<Ticket> emission to single Ticket emissions
                         * */
                        .flatMap(new Function<List<Ticket>, ObservableSource<Ticket>>() {
                            @Override
                            public ObservableSource<Ticket> apply(List<Ticket> tickets) throws Exception {
                                return Observable.fromIterable(tickets);
                            }
                        })
                        /**
                         * Fetching price on each Ticket emission
                         * */
                        .flatMap(new Function<Ticket, ObservableSource<Ticket>>() {
                            @Override
                            public ObservableSource<Ticket> apply(Ticket ticket) throws Exception {
                                return getPriceObservable(ticket);
                            }
                        })
                        .subscribeWith(new DisposableObserver<Ticket>() {

                            @Override
                            public void onNext(Ticket ticket) {
                                int position = ticketsList.indexOf(ticket);

                                if (position == -1) {
                                    // TODO - take action
                                    // Ticket not found in the list
                                    // This shouldn't happen
                                    return;
                                }

                                ticketsList.set(position, ticket);
                                mAdapter.notifyItemChanged(position);
                            }

                            @Override
                            public void onError(Throwable e) {
                                showError(e);
                            }

                            @Override
                            public void onComplete() {

                            }
                        }));

        // Calling connect to start emission
        ticketsObservable.connect();
    }

    /**
     * Making Retrofit call to fetch all tickets
     */
    private Observable<List<Ticket>> getTickets(String from, String to) {
        return apiService.searchTickets(from, to)
                .toObservable()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    /**
     * Making Retrofit call to get single ticket price
     * get price HTTP call returns Price object, but
     * map() operator is used to change the return type to Ticket
     */
    private Observable<Ticket> getPriceObservable(final Ticket ticket) {
        return apiService
                .getPrice(ticket.getFlightNumber(), ticket.getFrom(), ticket.getTo())
                .toObservable()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .map(new Function<Price, Ticket>() {
                    @Override
                    public Ticket apply(Price price) throws Exception {
                        ticket.setPrice(price);
                        return ticket;
                    }
                });
    }


    public class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {

        private int spanCount;
        private int spacing;
        private boolean includeEdge;

        public GridSpacingItemDecoration(int spanCount, int spacing, boolean includeEdge) {
            this.spanCount = spanCount;
            this.spacing = spacing;
            this.includeEdge = includeEdge;
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view); // item position
            int column = position % spanCount; // item column

            if (includeEdge) {
                outRect.left = spacing - column * spacing / spanCount; // spacing - column * ((1f / spanCount) * spacing)
                outRect.right = (column + 1) * spacing / spanCount; // (column + 1) * ((1f / spanCount) * spacing)

                if (position < spanCount) { // top edge
                    outRect.top = spacing;
                }
                outRect.bottom = spacing; // item bottom
            } else {
                outRect.left = column * spacing / spanCount; // column * ((1f / spanCount) * spacing)
                outRect.right = spacing - (column + 1) * spacing / spanCount; // spacing - (column + 1) * ((1f /    spanCount) * spacing)
                if (position >= spanCount) {
                    outRect.top = spacing; // item top
                }
            }
        }
    }

    private int dpToPx(int dp) {
        Resources r = getResources();
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, r.getDisplayMetrics()));
    }


    private void showError(Throwable e) {
        Log.e(TAG, "showError: " + e.getMessage());


      //  snackbar.show();
    }

    @Override
    public void onTicketSelected(Ticket contact) {

    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        disposable.dispose();
        unbinder.unbind();
    }
}
