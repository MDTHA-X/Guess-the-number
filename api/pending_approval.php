<?php
require_once 'includes/db.php';

if (!isset($_SESSION['user_id'])) {
    header('Location: index.php');
    exit;
}

$user_id = $_SESSION['user_id'];

// Initial DB check on load - enables instant redirect if already approved
try {
    $stmt_user = $pdo->prepare("SELECT is_approved FROM users WHERE id = ?");
    $stmt_user->execute([$user_id]);
    $is_approved = (int)$stmt_user->fetchColumn();
    
    if ($is_approved) {
        $_SESSION['is_approved'] = 1;
        header('Location: dashboard.php');
        exit;
    }
} catch (PDOException $e) {}
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Approval Pending - Marvelous Reminder</title>
    <link rel="stylesheet" href="assets/css/style.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
</head>
<body>
    <div class="container hero">
        <div class="auth-container animate-fade-in">
            <div class="glass-card auth-card">
                <i class="fas fa-hourglass-half fa-3x" style="color: var(--secondary); margin-bottom: 1.5rem;"></i>
                <h2>Access Pending</h2>
                <p class="lead" style="font-size: 1.1rem; margin-top: 1rem;">
                    Hello, <span style="color: #fff;"><?php echo htmlspecialchars($_SESSION['name'] ?? 'User'); ?></span>.
                </p>
                <p style="color: var(--text-muted); margin-bottom: 2rem;">
                    Your account has been restricted to the <strong>@juniv.edu</strong> domain, but an administrator needs to manually approve your access to the dashboard.
                </p>
                <div style="background: rgba(255,255,255,0.05); padding: 1rem; border-radius: 12px; border: 1px solid var(--glass-border); margin-bottom: 2rem;">
                    <p style="font-size: 0.9rem;">Please contact the supreme admin at:<br>
                    <strong style="color: var(--primary);"><?php echo defined('ADMIN_EMAIL') ? ADMIN_EMAIL : 'the administrator'; ?></strong></p>
                </div>
                <div style="margin-bottom: 2.5rem;">
                    <p style="font-size: 0.85rem; color: var(--primary);"><i class="fas fa-sync fa-spin"></i> Checking for approval status in real-time...</p>
                </div>
                <a href="logout.php" class="btn btn-outline">
                    <i class="fas fa-sign-out-alt"></i> Logout & Try Again later
                </a>
            </div>
        </div>
    </div>

    <!-- The poller will automatically reload the page once $_SESSION['is_approved'] updates via the API -->
    <script src="assets/js/notifications.js"></script>
</body>
</html>
