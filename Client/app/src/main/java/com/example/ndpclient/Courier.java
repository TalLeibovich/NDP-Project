package com.example.ndpclient;

public class Courier {
    private String courierId;
    private String companyId;
    private String name;

    public Courier(String courierId, String companyId, String name) {
        this.courierId = courierId;
        this.companyId = companyId;
        this.name = name;
    }

    public String getCourierId() {
        return courierId;
    }

    public String getCompanyId() {
        return companyId;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name + " (" + courierId + ")";
    }
}