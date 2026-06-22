package com.example.ndpclient;

public class Courier {
    private String courierId;
    private String companyId;
    private String name;

    // Creates a courier model with its company association.
    public Courier(String courierId, String companyId, String name) {
        this.courierId = courierId;
        this.companyId = companyId;
        this.name = name;
    }

    // Returns the courier identifier.
    public String getCourierId() {
        return courierId;
    }

    // Returns the company identifier associated with the courier.
    public String getCompanyId() {
        return companyId;
    }

    // Returns the courier display name.
    public String getName() {
        return name;
    }

    // Returns the display format used in selection lists.
    @Override
    public String toString() {
        return name + " (" + courierId + ")";
    }
}