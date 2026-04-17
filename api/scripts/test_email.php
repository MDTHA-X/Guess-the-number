<?php
/**
 * SMTP Connection Test Script
 */

require_once __DIR__ . '/../includes/mailer.php';

$test_email = 'tanjimxyt@gmail.com';
$subject = "SMTP Test - Marvelous Reminder";
$body = "<h1>SMTP Configuration Working!</h1><p>This is a test email from the Marvelous Reminder system (CSE 32 Notification Panel) using the provided Gmail App Password.</p>";

echo "Attempting to send test email to $test_email...\n";

if (sendEmail($test_email, $subject, $body)) {
    echo "SUCCESS: Email sent successfully! Check your inbox.\n";
} else {
    echo "FAILURE: Email failed to send. Check PHP error logs or PHPMailer output.\n";
}
