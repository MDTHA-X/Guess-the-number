<?php
require_once 'includes/db.php';
require_once 'includes/mailer.php';

if (!isset($_SESSION['user_id']) || ($_SESSION['role'] !== 'admin' && $_SESSION['role'] !== 'supreme_admin')) {
    header('Location: dashboard.php');
    exit;
}

$user_id = $_SESSION['user_id'];
$role = $_SESSION['role'];

// --- Session Synchronization & Security Check ---
try {
    $stmt_sync = $pdo->prepare("SELECT role, is_approved FROM users WHERE id = ?");
    $stmt_sync->execute([$user_id]);
    $db_user = $stmt_sync->fetch();
    
    if ($db_user) {
        $_SESSION['role'] = $db_user['role'];
        $_SESSION['is_approved'] = (int)$db_user['is_approved'];
        $role = $_SESSION['role']; // Update local variable
    } else {
        // CRITICAL: Account was deleted!
        session_destroy();
        header('Location: index.php?error=account_deleted');
        exit;
    }
} catch (PDOException $e) { /* Fail silently */ }

// Handle Actions
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $target_id = $_POST['target_id'] ?? 0;
    $action = $_POST['admin_action'] ?? '';

    try {
        // Find target user
        $stmt = $pdo->prepare("SELECT * FROM users WHERE id = ?");
        $stmt->execute([$target_id]);
        $target_user = $stmt->fetch();

        if ($target_user) {
            // Supreme Admin Protection: Cannot revoke/demote supreme admin
            if ($target_user['role'] === 'supreme_admin') {
                $error = "Cannot modify the Supreme Admin.";
            } else {
                if ($action === 'approve') {
                    $stmt = $pdo->prepare("UPDATE users SET is_approved = 1 WHERE id = ?");
                    $stmt->execute([$target_id]);

                    // Send Approval Email
                    $subject = "Account Approved - Marvelous Reminder";
                    $base_path = rtrim(dirname($_SERVER['PHP_SELF']), '/\\');
                    $login_url = (isset($_SERVER['HTTPS']) ? "https" : "http") . "://" . $_SERVER['HTTP_HOST'] . $base_path . "/index.php";
                    
                    $body = "
                    <div style='background: #0f172a; color: #f8fafc; padding: 40px; font-family: sans-serif; border-radius: 20px; max-width: 600px; margin: auto;'>
                        <h1 style='color: #6366f1; text-align: center;'>Congratulations!</h1>
                        <p style='font-size: 1.1rem; line-height: 1.6;'>Hello <strong>{$target_user['name']}</strong>,</p>
                        <p style='font-size: 1rem; line-height: 1.6; color: #94a3b8;'>Your account for the <strong>Marvelous Reminder</strong> platform has been officially approved by the administrator.</p>
                        <p style='font-size: 1rem; line-height: 1.6; color: #94a3b8;'>You can now access the global space, create personalized reminders, and receive real-time alerts for your deadlines.</p>
                        <div style='text-align: center; margin-top: 30px;'>
                            <a href='{$login_url}' style='background: linear-gradient(135deg, #6366f1, #a855f7); color: white; padding: 15px 30px; text-decoration: none; border-radius: 12px; font-weight: bold; display: inline-block; box-shadow: 0 4px 15px rgba(99, 102, 241, 0.4);'>Go to Dashboard</a>
                        </div>
                        <p style='text-align: center; margin-top: 15px; font-size: 0.8rem;'>
                            <a href='{$login_url}' style='color: #6366f1; text-decoration: underline;'>{$login_url}</a>
                        </p>
                        <p style='margin-top: 40px; font-size: 0.8rem; color: #64748b; text-align: center; border-top: 1px solid rgba(255,255,255,0.1); padding-top: 20px;'>
                            Ensuring you never miss a deadline.<br>
                            &copy; " . date('Y') . " Marvelous Reminder Team
                        </p>
                    </div>";
                    
                    sendEmail($target_user['email'], $subject, $body);
                } elseif ($action === 'revoke') {
                    $stmt = $pdo->prepare("UPDATE users SET is_approved = 0 WHERE id = ?");
                    $stmt->execute([$target_id]);
                } elseif ($action === 'promote' && $role === 'supreme_admin') {
                    $stmt = $pdo->prepare("UPDATE users SET role = 'admin' WHERE id = ?");
                    $stmt->execute([$target_id]);
                } elseif ($action === 'demote' && $role === 'supreme_admin') {
                    $stmt = $pdo->prepare("UPDATE users SET role = 'user' WHERE id = ?");
                    $stmt->execute([$target_id]);
                } elseif ($action === 'delete' && $role === 'supreme_admin') {
                    $stmt = $pdo->prepare("DELETE FROM users WHERE id = ?");
                    $stmt->execute([$target_id]);
                }
                $success = "Action performed successfully.";
            }
        }
    } catch (PDOException $e) {
        $error = "Database error: " . $e->getMessage();
    }
}

// Fetch users
$users = $pdo->query("SELECT * FROM users ORDER BY created_at DESC")->fetchAll();
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Admin Panel - Marvelous Reminder</title>
    <link rel="stylesheet" href="assets/css/style.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <style>
        table {
            width: 100%;
            border-collapse: collapse;
            margin-top: 2rem;
        }
        th, td {
            text-align: left;
            padding: 1rem;
            border-bottom: 1px solid var(--glass-border);
        }
        tr:hover { background: rgba(255,255,255,0.02); }
        .action-btns { display: flex; gap: 0.5rem; }
    </style>
</head>
<body>
    <div class="container animate-fade-in">
        <header style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 2rem;">
            <div>
                <a href="dashboard.php" style="color: var(--text-muted); text-decoration: none;">
                    <i class="fas fa-arrow-left"></i> Back to Dashboard
                </a>
                <h2 style="margin-top: 0.5rem;">User Management</h2>
            </div>
            <div style="display: flex; align-items: center; gap: 1rem;">
                <button class="btn btn-outline" id="testSound" title="Test Alerts" style="padding: 0.5rem 1rem;">
                    <i class="fas fa-volume-up"></i>
                </button>
                <div class="badge badge-upcoming" style="padding: 0.5rem 1rem;">
                    Logged in as: <?php echo htmlspecialchars($role); ?>
                </div>
            </div>
        </header>

        <?php if (isset($error)): ?>
            <div class="glass-card" style="border-color: var(--accent); color: var(--accent); margin-bottom: 1rem; padding: 1rem;">
                <i class="fas fa-exclamation-triangle"></i> <?php echo $error; ?>
            </div>
        <?php endif; ?>

        <?php if (isset($success)): ?>
            <div class="glass-card" style="border-color: #22c55e; color: #22c55e; margin-bottom: 1rem; padding: 1rem;">
                <i class="fas fa-check-circle"></i> <?php echo $success; ?>
            </div>
        <?php endif; ?>

        <div class="glass-card" style="padding: 0;">
            <table>
                <thead>
                    <tr>
                        <th>User</th>
                        <th>Email</th>
                        <th>Role</th>
                        <th>Status</th>
                        <th>Actions</th>
                    </tr>
                </thead>
                <tbody>
                    <?php foreach ($users as $u): ?>
                        <tr>
                            <td>
                                <strong><?php echo htmlspecialchars($u['name']); ?></strong>
                            </td>
                            <td><?php echo htmlspecialchars($u['email']); ?></td>
                            <td>
                                <span class="badge <?php echo $u['role'] === 'supreme_admin' ? 'badge-urgent' : 'badge-upcoming'; ?>">
                                    <?php echo $u['role']; ?>
                                </span>
                            </td>
                            <td>
                                <?php if ($u['is_approved']): ?>
                                    <span style="color: #22c55e;"><i class="fas fa-check"></i> Approved</span>
                                <?php else: ?>
                                    <span style="color: var(--accent);"><i class="fas fa-clock"></i> Pending</span>
                                <?php endif; ?>
                            </td>
                            <td class="action-btns">
                                <form method="POST" style="display:inline;">
                                    <input type="hidden" name="target_id" value="<?php echo $u['id']; ?>">
                                    
                                    <?php if (!$u['is_approved']): ?>
                                        <button type="submit" name="admin_action" value="approve" class="btn btn-outline" style="padding: 0.3rem 0.6rem; color: #22c55e;">
                                            Approve
                                        </button>
                                    <?php else: ?>
                                        <button type="submit" name="admin_action" value="revoke" class="btn btn-outline" style="padding: 0.3rem 0.6rem; color: var(--accent);">
                                            Revoke
                                        </button>
                                    <?php endif; ?>

                                    <?php if ($role === 'supreme_admin' && $u['role'] !== 'supreme_admin'): ?>
                                        <?php if ($u['role'] === 'user'): ?>
                                            <button type="submit" name="admin_action" value="promote" class="btn btn-outline" style="padding: 0.3rem 0.6rem; color: var(--primary);">
                                                Make Admin
                                            </button>
                                        <?php else: ?>
                                            <button type="submit" name="admin_action" value="demote" class="btn btn-outline" style="padding: 0.3rem 0.6rem;">
                                                Demote
                                            </button>
                                        <?php endif; ?>
                                        <button type="submit" name="admin_action" value="delete" class="btn btn-outline" style="padding: 0.3rem 0.6rem; color: var(--accent);" onclick="return confirm('Delete user and all their reminders?')">
                                            <i class="fas fa-trash"></i>
                                        </button>
                                    <?php endif; ?>
                                </form>
                            </td>
                        </tr>
                    <?php endforeach; ?>
                </tbody>
            </table>
        </div>
    </div>
    
    <script src="assets/js/notifications.js"></script>
</body>
</html>
