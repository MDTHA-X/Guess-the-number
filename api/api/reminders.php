<?php
/**
 * API for Reminder CRUD Operations
 * Perfectly balanced Sanitized version.
 */

// Include database logic
require_once '../includes/db.php';

// Auth check - Must be logged in
if (!isset($_SESSION['user_id'])) {
    header('HTTP/1.1 403 Forbidden');
    exit('Unauthorized');
}

// User Context
$user_id = $_SESSION['user_id'];
$user_role = $_SESSION['role'] ?? 'user';
$is_approved_user = (int)($_SESSION['is_approved'] ?? 0);

// --- POST REQUESTS: HANDLES ADD, EDIT, DONE, DELETE ---
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    
    // Approval Required for all mutations
    if (!$is_approved_user) {
        header('HTTP/1.1 403 Forbidden');
        exit('Approval Required');
    }

    $action = $_POST['action'] ?? '';

    // A. ADD REMINDER
    if ($action === 'add') {
        $title = $_POST['title'] ?? 'New Reminder';
        $description = $_POST['description'] ?? '';
        $category = $_POST['category'] ?? 'Class';
        $deadline = $_POST['deadline'] ?? '';
        
        // Calculate interval in minutes
        $interval = (int)($_POST['remind_interval'] === 'custom' ? $_POST['custom_interval'] : $_POST['remind_interval']);
        if ($_POST['remind_interval'] === 'custom' && ($_POST['custom_unit'] ?? 'mins') === 'hours') {
            $interval *= 60;
        }
        
        if (empty($deadline)) {
            header('Location: ../dashboard.php?error=empty_deadline');
            exit;
        }

        // Global logic restricted to Admins
        $is_global = ($user_role === 'admin' || $user_role === 'supreme_admin') ? (int)($_POST['is_global'] ?? 0) : 0;

        try {
            $stmt = $pdo->prepare("INSERT INTO reminders (user_id, title, description, category, is_global, deadline, remind_interval) VALUES (?, ?, ?, ?, ?, ?, ?)");
            $stmt->execute([$user_id, $title, $description, $category, $is_global, $deadline, $interval]);
            header('Location: ../dashboard.php?success=added');
            exit;
        } catch (PDOException $e) {
            die("Error adding reminder: " . $e->getMessage());
        }
    }

    // B. EDIT REMINDER
    if ($action === 'edit') {
        $id = (int)($_POST['id'] ?? 0);
        $title = $_POST['title'] ?? '';
        $description = $_POST['description'] ?? '';
        $category = $_POST['category'] ?? 'Class';
        $deadline = $_POST['deadline'] ?? '';
        
        $interval = (int)($_POST['remind_interval'] === 'custom' ? $_POST['custom_interval'] : $_POST['remind_interval']);
        if ($_POST['remind_interval'] === 'custom' && ($_POST['custom_unit'] ?? 'mins') === 'hours') {
            $interval *= 60;
        }

        try {
            // Verify Edit Rights
            $can_edit = ($user_role === 'admin' || $user_role === 'supreme_admin');
            if (!$can_edit) {
                $stmt_check = $pdo->prepare("SELECT user_id FROM reminders WHERE id = ?");
                $stmt_check->execute([$id]);
                if ($stmt_check->fetchColumn() == $user_id) $can_edit = true;
            }

            if ($can_edit) {
                $sql = "UPDATE reminders SET title = ?, description = ?, category = ?, deadline = ?, remind_interval = ?";
                $params = [$title, $description, $category, $deadline, $interval];
                
                if ($user_role === 'admin' || $user_role === 'supreme_admin') {
                    $sql .= ", is_global = ?";
                    $params[] = (int)($_POST['is_global'] ?? 0);
                }
                
                $sql .= " WHERE id = ?";
                $params[] = $id;
                
                $stmt = $pdo->prepare($sql);
                $stmt->execute($params);
                header('Location: ../dashboard.php?success=updated');
                exit;
            } else {
                header('Location: ../dashboard.php?error=unauthorized');
                exit;
            }
        } catch (PDOException $e) {
            die("Error updating reminder: " . $e->getMessage());
        }
    }

    // C. MARK AS DONE
    if ($action === 'done') {
        $id = (int)($_POST['id'] ?? 0);
        try {
            $stmt = $pdo->prepare("UPDATE reminders SET status = 'completed' WHERE id = ? AND (user_id = ? OR ? IN ('admin', 'supreme_admin'))");
            $stmt->execute([$id, $user_id, $user_role]);
            header('Location: ../dashboard.php?success=completed');
            exit;
        } catch (PDOException $e) {
            die("Error completing reminder: " . $e->getMessage());
        }
    }

    // D. DELETE REMINDER
    if ($action === 'delete') {
        $id = (int)($_POST['id'] ?? 0);
        try {
            $stmt = $pdo->prepare("DELETE FROM reminders WHERE id = ? AND (user_id = ? OR ? IN ('admin', 'supreme_admin'))");
            $stmt->execute([$id, $user_id, $user_role]);
            header('Location: ../dashboard.php?success=deleted');
            exit;
        } catch (PDOException $e) {
            die("Error deleting reminder: " . $e->getMessage());
        }
    }
}


// --- GET REQUESTS: HANDLES POLLING ---
if ($_SERVER['REQUEST_METHOD'] === 'GET' && isset($_GET['poll'])) {
    header('Content-Type: application/json');
    
    try {
        // 1. Sync Session State with Database
        $stmt_sync = $pdo->prepare("SELECT role, is_approved FROM users WHERE id = ?");
        $stmt_sync->execute([$user_id]);
        $db_data = $stmt_sync->fetch();
        
        $session_updates = ['role_changed' => false, 'status_changed' => false, 'account_deleted' => false];
        if ($db_data) {
            if ($db_data['role'] !== $_SESSION['role']) {
                $_SESSION['role'] = $db_data['role'];
                $session_updates['role_changed'] = true;
            }
            if ((int)$db_data['is_approved'] !== (int)$_SESSION['is_approved']) {
                $_SESSION['is_approved'] = (int)$db_data['is_approved'];
                $session_updates['status_changed'] = true;
            }
        } else {
            // CRITICAL: User is gone!
            session_destroy();
            $session_updates['account_deleted'] = true;
        }

        // 2. Poll Notifications (Per-user tracking)
        $notifications = [];
        if ($is_approved_user) {
            $stmt_notif = $pdo->prepare("
                SELECT r.*, u.name as creator_name, un.last_notified_at as my_last_notified
                FROM reminders r
                JOIN users u ON r.user_id = u.id
                LEFT JOIN user_notifications un ON r.id = un.reminder_id AND un.user_id = ?
                WHERE (r.user_id = ? OR r.is_global = 1)
                AND r.status = 'pending' 
                AND (
                    un.last_notified_at IS NULL 
                    OR (r.deadline > NOW() AND TIMESTAMPDIFF(MINUTE, un.last_notified_at, NOW()) >= r.remind_interval)
                )
            ");
            $stmt_notif->execute([$user_id, $user_id]);
            $notifications = $stmt_notif->fetchAll();
            
            if (!empty($notifications)) {
                $stmt_upd = $pdo->prepare("
                    INSERT INTO user_notifications (user_id, reminder_id, last_notified_at) 
                    VALUES (?, ?, NOW()) 
                    ON DUPLICATE KEY UPDATE last_notified_at = NOW()
                ");
                foreach ($notifications as $n) {
                    $stmt_upd->execute([$user_id, $n['id']]);
                }
            }
        }

        // 3. UI Synchronization Hash
        $ui_hash = null;
        if ($is_approved_user) {
            $stmt_hash = $pdo->prepare("SELECT id FROM reminders WHERE (user_id = ? OR is_global = 1) AND status = 'pending' AND deadline > NOW()");
            $stmt_hash->execute([$user_id]);
            $current_ids = $stmt_hash->fetchAll(PDO::FETCH_COLUMN);
            $ui_hash = md5(json_encode($current_ids));
        }

        echo json_encode([
            'notifications' => $notifications,
            'ui_hash' => $ui_hash,
            'session_sync' => $session_updates
        ]);
        exit;

    } catch (PDOException $e) {
        echo json_encode(['error' => $e->getMessage()]);
        exit;
    }
}
?>
