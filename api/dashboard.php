<?php
/**
 * Dashboard for Reminder Web App
 * Enhanced with Flatpickr for premium Date selection.
 */
require_once 'includes/db.php';

// Auth Protection
if (!isset($_SESSION['user_id'])) {
    header('Location: index.php');
    exit;
}

$user_id = $_SESSION['user_id'];

// --- Session Synchronization & Security Check ---
try {
    $stmt_sync = $pdo->prepare("SELECT role, is_approved FROM users WHERE id = ?");
    $stmt_sync->execute([$user_id]);
    $db_user = $stmt_sync->fetch();
    
    if ($db_user) {
        $_SESSION['role'] = $db_user['role'];
        $_SESSION['is_approved'] = (int)$db_user['is_approved'];
    } else {
        // CRITICAL: Account was deleted!
        session_destroy();
        header('Location: index.php?error=account_deleted');
        exit;
    }
} catch (PDOException $e) { /* Fail silently to maintain uptime if DB flickers */ }

// Approval Protection
if (!$_SESSION['is_approved']) {
    header('Location: pending_approval.php');
    exit;
}

$name = $_SESSION['name'] ?? 'User';
$role = $_SESSION['role'] ?? 'Guest';

// --- View Logic ---
$view = $_GET['view'] ?? 'active';
$show_global_only = ($view === 'global' && ($role === 'admin' || $role === 'supreme_admin'));
$show_expired = ($view === 'expired');

$query = "
    SELECT r.*, u.name as creator_name 
    FROM reminders r 
    JOIN users u ON r.user_id = u.id 
    WHERE r.status = 'pending' 
";

if ($show_expired) {
    $query .= " AND r.deadline < NOW() ";
} else {
    $query .= " AND r.deadline >= NOW() ";
}

if ($show_global_only) {
    $query .= " AND r.is_global = 1 ";
} else {
    $query .= " AND (r.user_id = $user_id OR r.is_global = 1) ";
}

$query .= " ORDER BY r.deadline ASC";

try {
    $stmt = $pdo->query($query);
    $all_reminders = $stmt->fetchAll();
} catch (PDOException $e) {
    $all_reminders = [];
}

$categories = ['Class', 'Exam', 'Event'];
$grouped_reminders = ['Class' => [], 'Exam' => [], 'Event' => []];
foreach ($all_reminders as $r) {
    if (in_array($r['category'], $categories)) {
        $grouped_reminders[$r['category']][] = $r;
    }
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Dashboard - Marvelous Reminder</title>
    
    <!-- Design Foundation -->
    <link rel="stylesheet" href="assets/css/style.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    
    <!-- Flatpickr (Premium Date Picker) -->
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/flatpickr/dist/flatpickr.min.css">
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/flatpickr/dist/themes/dark.css">
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/flatpickr/dist/plugins/confirmDate/confirmDate.css">
    
    <style>
        .header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 3rem; }
        .user-profile { display: flex; align-items: center; gap: 1rem; }
        .reminder-card { display: flex; flex-direction: column; justify-content: space-between; position: relative; transition: transform 0.3s ease; }
        .reminder-card:hover { transform: translateY(-5px); }
        .badge { padding: 0.2rem 0.6rem; border-radius: 99px; font-size: 0.7rem; font-weight: bold; }
        .badge-upcoming { background: rgba(99, 102, 241, 0.2); color: var(--primary); }
        .badge-urgent { background: rgba(244, 63, 94, 0.2); color: var(--accent); }
        
        input, textarea, select {
            width: 100%; background: rgba(255, 255, 255, 0.08); border: 1px solid var(--glass-border);
            border-radius: 12px; padding: 0.85rem; color: #fff; margin-bottom: 1rem; outline: none;
        }
        
        /* Flatpickr Custom Stying to Match Theme */
        .flatpickr-calendar { background: rgba(30, 41, 59, 0.95); backdrop-filter: blur(10px); border: 1px solid var(--glass-border); box-shadow: 0 8px 32px rgba(0,0,0,0.4); border-radius: 16px; }
        .flatpickr-day.selected { background: var(--primary); border-color: var(--primary); }
        .flatpickr-confirm { background: var(--primary) !important; color: white !important; font-weight: bold; cursor: pointer; padding: 10px !important; border-bottom-left-radius: 16px; border-bottom-right-radius: 16px; }

        .category-header { margin: 2.5rem 0 1.5rem 0; border-left: 4px solid var(--primary); padding-left: 1rem; display: flex; align-items: center; justify-content: space-between; }
        
        #editModal {
            display: none; position: fixed; top: 0; left: 0; width: 100%; height: 100%;
            background: rgba(0,0,0,0.6); backdrop-filter: blur(8px); z-index: 1000;
            justify-content: center; align-items: center;
        }
        .modal-content { max-width: 500px; width: 90%; }
        .card-actions { position: absolute; top: 1rem; right: 1rem; display: flex; gap: 0.5rem; }
        .icon-btn { background: none; border: none; color: var(--text-muted); cursor: pointer; padding: 0.3rem; border-radius: 6px; }
        .icon-btn:hover { background: rgba(255,255,255,0.1); color: white; }
    </style>
</head>
<body>
    <div class="container animate-fade-in">
        <header class="header glass-card" style="padding: 1rem 2rem; border-radius: 20px;">
            <div class="user-profile">
                <i class="fas fa-user-circle fa-2x" style="color: var(--primary);"></i>
                <div>
                    <h4 style="margin: 0;"><?php echo htmlspecialchars($name); ?></h4>
                    <span class="badge badge-upcoming"><?php echo htmlspecialchars($role); ?></span>
                </div>
            </div>
            <div style="display: flex; gap: 0.8rem; align-items: center;">
                <?php if ($role === 'supreme_admin' || $role === 'admin'): ?>
                    <a href="?view=<?php echo $show_global_only ? 'personal' : 'global'; ?>" class="btn <?php echo $show_global_only ? 'btn-primary' : 'btn-outline'; ?>" style="font-size: 0.75rem; padding: 0.5rem 1rem;">
                        <i class="fas fa-globe"></i> <?php echo $show_global_only ? 'Personal' : 'Global Area'; ?>
                    </a>
                    <a href="admin_dashboard.php" class="btn btn-outline" style="font-size: 0.75rem; padding: 0.5rem 1rem;">
                        <i class="fas fa-users-cog"></i> Users
                    </a>
                <?php endif; ?>

                <a href="?view=<?php echo $show_expired ? 'active' : 'expired'; ?>" class="btn <?php echo $show_expired ? 'btn-primary' : 'btn-outline'; ?>" style="font-size: 0.75rem; padding: 0.5rem 1rem;">
                    <i class="fas fa-history"></i> <?php echo $show_expired ? 'Active' : 'Expired'; ?>
                </a>

                <button class="btn btn-outline" id="testSound" title="Test Alerts" style="padding: 0.5rem 1rem;">
                    <i class="fas fa-volume-up"></i>
                </button>
                <a href="logout.php" class="btn btn-outline" title="Logout" style="padding: 0.5rem 1rem;">
                    <i class="fas fa-sign-out-alt"></i>
                </a>
            </div>
        </header>

        <div class="dashboard-grid" style="align-items: flex-start;">
            <!-- Create Section -->
            <?php if (!$show_expired): ?>
            <div class="glass-card">
                <h3 style="margin-bottom: 1.5rem;"><i class="fas fa-plus-circle"></i> New Reminder</h3>
                <form action="api/reminders.php" method="POST">
                    <input type="hidden" name="action" value="add">
                    <input type="text" name="title" placeholder="What needs to be done?" required>
                    <textarea name="description" placeholder="Add some details..." rows="2"></textarea>
                    
                    <label style="font-size: 0.8rem; color: var(--text-muted);">Category</label>
                    <select name="category">
                        <option value="Class">Class</option>
                        <option value="Exam">Exam</option>
                        <option value="Event">Event</option>
                    </select>

                    <div style="display: grid; grid-template-columns: 70% 1fr; gap: 0.8rem; align-items: flex-end;">
                        <div>
                            <label style="font-size: 0.8rem; color: var(--text-muted);">Deadline</label>
                            <input type="text" name="deadline" id="add-deadline" placeholder="Select Date & Time..." required style="margin-bottom: 0;">
                        </div>
                        <div>
                            <label style="font-size: 0.8rem; color: var(--text-muted); white-space: nowrap;">Interval</label>
                            <select name="remind_interval" class="ctx-interval-select" data-target="ctx-custom-add" style="margin-bottom: 0;">
                                <option value="15">15m</option>
                                <option value="30" selected>30m</option>
                                <option value="60">1h</option>
                                <option value="custom">Custom</option>
                            </select>
                        </div>
                    </div>

                    <div class="ctx-custom-add animate-fade-in" style="display: none; background: rgba(255,255,255,0.03); padding: 1rem; border-radius: 12px; margin: 1rem 0; border: 1px dashed var(--glass-border);">
                        <div style="display: flex; gap: 0.5rem; align-items: center;">
                            <input type="number" name="custom_interval" min="1" placeholder="Value" style="margin-bottom: 0; flex: 2;" value="10">
                            <select name="custom_unit" style="margin-bottom: 0; flex: 1.5;">
                                <option value="mins">Mins</option>
                                <option value="hours">Hours</option>
                            </select>
                            <button type="button" class="btn btn-primary" onclick="this.parentElement.parentElement.style.display='none'">OK</button>
                        </div>
                    </div>

                    <input type="hidden" name="is_global" value="<?php echo $show_global_only ? 1 : 0; ?>">
                    
                    <button type="submit" class="btn btn-primary" style="width: 100%; margin-top: 1rem;">
                        <i class="fas fa-plus"></i> Add to <?php echo $show_global_only ? 'Global' : 'Personal'; ?>
                    </button>
                </form>
            </div>
            <?php endif; ?>

            <!-- List Section -->
            <div style="grid-column: <?php echo $show_expired ? 'span 3' : 'span 2'; ?>;">
                <?php if ($show_expired): ?>
                    <div style="margin-bottom: 1.5rem; display: flex; align-items: center; gap: 0.8rem;">
                        <i class="fas fa-history fa-2x" style="color: var(--accent);"></i>
                        <h2 style="margin: 0;">Expired Reminders</h2>
                    </div>
                <?php endif; ?>

                <?php if (empty($all_reminders)): ?>
                    <div class="glass-card" style="text-align: center; padding: 4rem;">
                        <i class="fas fa-check-circle fa-3x" style="color: var(--primary); opacity: 0.3; margin-bottom: 1rem;"></i>
                        <p style="color: var(--text-muted);">No <?php echo $show_expired ? 'expired' : 'active'; ?> reminders here.</p>
                    </div>
                <?php endif; ?>

                <?php foreach ($categories as $cat): ?>
                    <?php if (!empty($grouped_reminders[$cat])): ?>
                        <div class="category-header">
                            <i class="fas <?php echo $cat === 'Class' ? 'fa-book' : ($cat === 'Exam' ? 'fa-pen-nib' : 'fa-calendar-star'); ?>"></i>
                            <h3><?php echo $cat; ?>s</h3>
                        </div>
                        <div class="dashboard-grid" style="grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); margin-bottom: 2rem;">
                            <?php foreach ($grouped_reminders[$cat] as $r): ?>
                                <?php 
                                    $deadline_ts = strtotime($r['deadline']);
                                    $urgent = ($deadline_ts - time()) < 3600; 
                                ?>
                                <div class="glass-card reminder-card animate-fade-in" style="border-top: 4px solid <?php echo $urgent ? 'var(--accent)' : 'var(--primary)'; ?>">
                                    <?php 
                                        $can_manage = ($role === 'admin' || $role === 'supreme_admin' || ($r['user_id'] == $user_id && !$r['is_global']));
                                        if ($can_manage): 
                                    ?>
                                    <div class="card-actions">
                                        <button class="icon-btn" onclick='openEditModal(<?php echo json_encode($r); ?>)' title="Edit">
                                            <i class="fas fa-edit"></i>
                                        </button>
                                        <form action="api/reminders.php" method="POST" style="display:inline;">
                                            <input type="hidden" name="action" value="delete">
                                            <input type="hidden" name="id" value="<?php echo $r['id']; ?>">
                                            <button type="submit" class="icon-btn btn-delete" title="Delete" onclick="return confirm('Delete this reminder?')">
                                                <i class="fas fa-trash"></i>
                                            </button>
                                        </form>
                                    </div>
                                    <?php endif; ?>
                                    <div>
                                        <div style="display: flex; gap: 0.5rem; flex-wrap: wrap; margin-bottom: 0.5rem;">
                                            <span class="badge <?php echo $urgent ? 'badge-urgent' : 'badge-upcoming'; ?>">
                                                <?php echo $urgent ? 'URGENT' : 'LATER'; ?>
                                            </span>
                                            <span class="badge" style="background: rgba(255,255,255,0.05); color: var(--text-muted);">
                                                <?php echo $r['is_global'] ? 'GLOBAL' : 'PRIVATE'; ?>
                                            </span>
                                        </div>
                                        <h4 style="margin: 0.2rem 0;"><?php echo htmlspecialchars($r['title']); ?></h4>
                                        <p style="font-size: 0.8rem; color: var(--text-muted);"><?php echo htmlspecialchars($r['description']); ?></p>
                                    </div>
                                    <div class="reminder-meta">
                                        <div style="margin-bottom: 0.5rem; font-weight: bold; color: var(--text-main);">
                                            <i class="fas fa-clock"></i> <?php echo date('d M, H:i', $deadline_ts); ?>
                                        </div>
                                        <div style="display: flex; <?php echo $can_manage ? 'justify-content: space-between;' : 'justify-content: flex-start;'; ?> align-items: center;">
                                            <span style="font-size: 0.7rem; color: var(--primary);">
                                                <i class="fas fa-user"></i> <?php echo htmlspecialchars($r['creator_name']); ?>
                                            </span>
                                            <?php if ($can_manage): ?>
                                            <form action="api/reminders.php" method="POST" style="display:inline;">
                                                <input type="hidden" name="action" value="done">
                                                <input type="hidden" name="id" value="<?php echo $r['id']; ?>">
                                                <button type="submit" class="btn btn-outline" style="padding: 0.2rem 0.5rem; font-size: 0.7rem; color: #22c55e; border-color: #22c55e;">Done</button>
                                            </form>
                                            <?php endif; ?>
                                        </div>
                                    </div>
                                </div>
                            <?php endforeach; ?>
                        </div>
                    <?php endif; ?>
                <?php endforeach; ?>
            </div>
        </div>
    </div>

    <!-- Edit Modal -->
    <div id="editModal">
        <div class="glass-card modal-content">
            <h3>Edit Reminder</h3>
            <form action="api/reminders.php" method="POST" style="margin-top: 1.5rem;">
                <input type="hidden" name="action" value="edit">
                <input type="hidden" name="id" id="edit-id">
                
                <label style="font-size: 0.8rem; color: var(--text-muted);">Title</label>
                <input type="text" name="title" id="edit-title" required>
                
                <label style="font-size: 0.8rem; color: var(--text-muted);">Description</label>
                <textarea name="description" id="edit-desc" rows="3"></textarea>
                
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 1rem;">
                    <div>
                        <label style="font-size: 0.8rem; color: var(--text-muted);">Deadline</label>
                        <input type="text" name="deadline" id="edit-deadline" placeholder="Select..." required>
                    </div>
                    <div>
                        <label style="font-size: 0.8rem; color: var(--text-muted);">Interval</label>
                        <select name="remind_interval" class="ctx-interval-select" id="edit-interval-select" data-target="ctx-custom-edit">
                            <option value="15">15m</option>
                            <option value="30">30m</option>
                            <option value="60">1h</option>
                            <option value="custom">Custom</option>
                        </select>
                    </div>
                </div>

                <div class="ctx-custom-edit animate-fade-in" style="display: none; background: rgba(255,255,255,0.03); padding: 1rem; border-radius: 12px; margin: 1rem 0; border: 1px dashed var(--glass-border);">
                    <div style="display: flex; gap: 0.5rem; align-items: center;">
                        <input type="number" name="custom_interval" id="edit-custom-interval" style="margin-bottom:0; flex:2;">
                        <select name="custom_unit" id="edit-custom-unit" style="margin-bottom:0; flex:1.5;">
                            <option value="mins">Mins</option>
                            <option value="hours">Hours</option>
                        </select>
                        <button type="button" class="btn btn-primary" onclick="this.parentElement.parentElement.style.display='none'">OK</button>
                    </div>
                </div>

                <div style="display: flex; gap: 1rem; margin-top: 1rem;">
                    <button type="button" class="btn btn-outline" style="flex:1" onclick="closeEditModal()">Cancel</button>
                    <button type="submit" class="btn btn-primary" style="flex:1">Save Changes</button>
                </div>
            </form>
        </div>
    </div>

    <!-- JS Libraries -->
    <script src="assets/js/notifications.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/flatpickr"></script>
    <script src="https://cdn.jsdelivr.net/npm/flatpickr/dist/plugins/confirmDate/confirmDate.js"></script>
    
    <script>
        // --- 1. Initialize Floatpickr ---
        const fpConfig = {
            enableTime: true,
            dateFormat: "Y-m-d H:i",
            time_24hr: false,
            minDate: new Date(), // Precise real-time constraint
            disableMobile: "true", // Force custom UI even on mobile for consistency
            plugins: [new confirmDatePlugin({
                confirmText: "OK",
                showAlways: true,
                theme: "dark"
            })]
        };

        const addFP = flatpickr("#add-deadline", fpConfig);
        const editFP = flatpickr("#edit-deadline", fpConfig);

        // --- 2. UI Form Logic ---
        document.querySelectorAll('.ctx-interval-select').forEach(sel => {
            sel.addEventListener('change', function() {
                const target = document.querySelector('.' + this.dataset.target);
                if(target) target.style.display = this.value === 'custom' ? 'block' : 'none';
            });
        });

        const modal = document.getElementById('editModal');
        function openEditModal(data) {
            document.getElementById('edit-id').value = data.id;
            document.getElementById('edit-title').value = data.title;
            document.getElementById('edit-desc').value = data.description;
            
            // Set Flatpickr value
            editFP.setDate(data.deadline);
            
            const intervalSel = document.getElementById('edit-interval-select');
            const customInp = document.getElementById('edit-custom-interval');
            const customUnit = document.getElementById('edit-custom-unit');
            const val = parseInt(data.remind_interval);

            if ([15, 30, 60].includes(val)) {
                intervalSel.value = val;
                document.querySelector('.ctx-custom-edit').style.display = 'none';
            } else {
                intervalSel.value = 'custom';
                document.querySelector('.ctx-custom-edit').style.display = 'block';
                if (val >= 60 && val % 60 === 0) {
                    customInp.value = val / 60;
                    customUnit.value = 'hours';
                } else {
                    customInp.value = val;
                    customUnit.value = 'mins';
                }
            }
            modal.style.display = 'flex';
        }

        function closeEditModal() { modal.style.display = 'none'; }
        window.onclick = function(e) { if (e.target == modal) closeEditModal(); }
    </script>
</body>
</html>
