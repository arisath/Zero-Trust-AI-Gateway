package com.securellm.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Controller to verify outbound network connectivity.
 */
@RestController
@RequestMapping("/api/v1")
public class ConnectivityController {

    private static final String CHECK_HOST = "8.8.8.8";
    private static final int CHECK_PORT = 53;
    private static final int TIMEOUT_MS = 2000;

    /**
     * Checks if the service can reach the external internet via a socket connection.
     * * @return 200 OK if successful, 503 Service Unavailable if connection fails.
     */
    @GetMapping("/check-connectivity")
    public ResponseEntity<String> checkConnectivity() {
        try (Socket socket = new Socket()) {
            // Using try-with-resources ensures the socket is closed automatically
            socket.connect(new InetSocketAddress(CHECK_HOST, CHECK_PORT), TIMEOUT_MS);
            return ResponseEntity.ok("Connected to internet");
        } catch (IOException e) {
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("No internet connection: " + e.getMessage());
        }
    }
}