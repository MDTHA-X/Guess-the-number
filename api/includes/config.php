<?php
/**
 * Configuration file for the Reminder Web App
 */

// Database configuration
define('DB_HOST', 'localhost');
define('DB_NAME', 'linayaco_wp299');
define('DB_USER', 'linayaco_wp299');
define('DB_PASS', 'weareMDTHA1');

// Google OAuth configuration
// IMPORTANT: Replace these with your actual credentials from Google Cloud Console
define('GOOGLE_CLIENT_ID', '1086115343482-4q9e1354i4jouku7hcpskt38ilbndm1v.apps.googleusercontent.com');
define('GOOGLE_CLIENT_SECRET', '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');
define('GOOGLE_REDIRECT_URL', 'https://notices.i-inaya.com/callback.php');
//define('GOOGLE_REDIRECT_URL', 'http://localhost:8000/callback.php');

// Application settings
define('ADMIN_EMAIL', '20230657075@juniv.edu'); // Supreme Admin
define('RESTRICTED_DOMAIN', 'juniv.edu');

// Start session if not already started
if (session_status() === PHP_SESSION_NONE) {
    session_start();
}
