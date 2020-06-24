package com.example.flightbookrx.model;

import com.google.gson.annotations.SerializedName;

public class Price {
    float price;
    String seats;



    public float getPrice() {
        return price;
    }

    public String getSeats() {
        return seats;
    }

    public String getCurrency() {
        return currency;
    }

    String currency;

    public String getFlightNumber() {
        return flightNumber;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    @SerializedName("flight_number")
    String flightNumber;

    String from;
    String to;
}
