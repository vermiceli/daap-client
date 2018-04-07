package org.mult.daap.db.entity;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import android.support.annotation.NonNull;

import org.mult.daap.model.Server;

@Entity(tableName = "servers")
public class ServerEntity implements Server {
    @PrimaryKey
    @NonNull
    private String address;

    private String password;

    public String getAddress() {
        return address;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Retrieves the port associated with the server from the address.
     * Defaults to 3689 if none is found
     *
     * @return The port associated with the server
     */
    public int getPort() {
        int port = 3689;
        if (this.getAddress() == null || this.getAddress().isEmpty()) {
            return port;
        }

        String[] urlAddress = this.getAddress().split(":");

        if (urlAddress.length == 2) { // port specified
            port = Integer.valueOf(urlAddress[1]);
        } else if (urlAddress.length > 2) { // ipv6
            port = Integer.valueOf(urlAddress[urlAddress.length - 1]);
        }

        return port;
    }

    public boolean loginRequired() {
        return this.getPassword() != null && !this.getPassword().isEmpty();
    }

    public ServerEntity(String address, String password) {
        this.address = address;
        this.password = password;
    }
}