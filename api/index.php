<?php
require_once 'includes/config.php';

// If user is already logged in, redirect to dashboard
if (isset($_SESSION['user_id'])) {
    header('Location: dashboard.php');
    exit;
}

// Construct Google Auth URL
$authUrl = "https://accounts.google.com/o/oauth2/v2/auth?" . http_build_query([
    'client_id' => GOOGLE_CLIENT_ID,
    'redirect_uri' => GOOGLE_REDIRECT_URL,
    'response_type' => 'code',
    'scope' => 'openid email profile',
    'access_type' => 'offline',
    'prompt' => 'consent'
]);
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Reminder - Stay on Track</title>
    <link rel="stylesheet" href="assets/css/style.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
</head>
<body>
    <div class="container hero">
        <div class="hero-content animate-fade-in">
            <div class="glass-card">
                <i class="fas fa-bell-slash fa-3x" style="color: var(--primary); margin-bottom: 1.5rem;"></i>
                <h1>Marvelous Reminder</h1>
                <p class="lead">The ultimate education-focused reminder system. Stay organized, hit your deadlines, and never miss a beat at JU.</p>
                
                <div style="margin-top: 2.5rem;">
                    <a href="<?php echo $authUrl; ?>" class="btn btn-primary btn-lg" style="padding: 1rem 2rem; font-size: 1.1rem; display: inline-flex; align-items: center; gap: 0.75rem;">
                        <i class="fab fa-google"></i>
                        Login with Education Gmail
                    </a>
                </div>
                
                <p style="margin-top: 1.5rem; color: var(--text-muted); font-size: 0.9rem;">
                    Exclusive for <span style="color: var(--secondary);">@juniv.edu</span> domain.
                </p>
            </div>
        </div>
    </div>

    <!-- Features Section Placeholder -->
    <div class="container" style="padding-top: 0;">
        <div class="dashboard-grid">
            <div class="glass-card animate-fade-in" style="animation-delay: 0.2s;">
                <i class="fas fa-clock fa-2x" style="color: var(--accent); margin-bottom: 1rem;"></i>
                <h3>Smart Intervals</h3>
                <p style="color: var(--text-muted); margin-top: 0.5rem;">Get reminded exactly when you need it, from every 15 minutes to customized windows.</p>
            </div>
            <div class="glass-card animate-fade-in" style="animation-delay: 0.3s;">
                <i class="fas fa-shield-alt fa-2x" style="color: var(--primary); margin-bottom: 1rem;"></i>
                <h3>Secure Access</h3>
                <p style="color: var(--text-muted); margin-top: 0.5rem;">Restricted to JU students and faculty with manual admin approval for extra security.</p>
            </div>
            <div class="glass-card animate-fade-in" style="animation-delay: 0.4s;">
                <i class="fas fa-desktop fa-2x" style="color: var(--secondary); margin-bottom: 1rem;"></i>
                <h3>Real-time Alerts</h3>
                <p style="color: var(--text-muted); margin-top: 0.5rem;">Native browser notifications ensure you never miss a deadline even when browsing other tabs.</p>
            </div>
        </div>
    </div>

    <footer style="margin-top: auto; padding: 2rem; text-align: center; color: var(--text-muted); font-size: 0.8rem;">
        &copy; 2026 Marvelous Reminder App. Built for Jagannath University Community.
    </footer>
</body>
</html>
