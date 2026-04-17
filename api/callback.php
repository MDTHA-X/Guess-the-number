<?php
/**
 * Google OAuth Callback Handler
 */

require_once 'includes/db.php';

if (!isset($_GET['code'])) {
    header('Location: index.php?error=no_code');
    exit;
}

$code = $_GET['code'];

// 1. Exchange authorization code for access token
$tokenUrl = "https://oauth2.googleapis.com/token";
$postFields = [
    'code' => $code,
    'client_id' => GOOGLE_CLIENT_ID,
    'client_secret' => GOOGLE_CLIENT_SECRET,
    'redirect_uri' => GOOGLE_REDIRECT_URL,
    'grant_type' => 'authorization_code'
];

$ch = curl_init($tokenUrl);
curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
curl_setopt($ch, CURLOPT_POSTFIELDS, http_build_query($postFields));
$response = curl_exec($ch);
curl_close($ch);

$tokenData = json_decode($response, true);

if (isset($tokenData['error'])) {
    header('Location: index.php?error=token_exchange_failed');
    exit;
}

$accessToken = $tokenData['access_token'];

// 2. Fetch user profile
$userInfoUrl = "https://www.googleapis.com/oauth2/v3/userinfo";
$ch = curl_init($userInfoUrl);
curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
curl_setopt($ch, CURLOPT_HTTPHEADER, ["Authorization: Bearer $accessToken"]);
$userInfo = json_decode(curl_exec($ch), true);
curl_close($ch);

if (!$userInfo || !isset($userInfo['email'])) {
    header('Location: index.php?error=user_info_failed');
    exit;
}

$email = $userInfo['email'];
$name = $userInfo['name'];
$google_id = $userInfo['sub'];

// 3. Domain Restriction Check
if (!str_ends_with($email, '@' . RESTRICTED_DOMAIN)) {
    header('Location: index.php?error=invalid_domain');
    exit;
}

// 4. Database logic
try {
    // Check if user exists
    $stmt = $pdo->prepare("SELECT * FROM users WHERE google_id = ?");
    $stmt->execute([$google_id]);
    $user = $stmt->fetch();

    $role = 'user';
    $is_approved = 0;

    // Check if this is the Supreme Admin
    if ($email === ADMIN_EMAIL) {
        $role = 'supreme_admin';
        $is_approved = 1; // Auto-approved
    }

    if (!$user) {
        // Register new user
        $stmt = $pdo->prepare("INSERT INTO users (google_id, name, email, role, is_approved) VALUES (?, ?, ?, ?, ?)");
        $stmt->execute([$google_id, $name, $email, $role, $is_approved]);
        $user_id = $pdo->lastInsertId();
    } else {
        // Update existing user (in case name changed or role needs upgrading to supreme_admin)
        $user_id = $user['id'];
        $current_role = $user['role'];
        $current_approval = $user['is_approved'];
        
        if ($email === ADMIN_EMAIL) {
            $current_role = 'supreme_admin';
            $current_approval = 1;
        }

        $stmt = $pdo->prepare("UPDATE users SET name = ?, role = ?, is_approved = ? WHERE id = ?");
        $stmt->execute([$name, $current_role, $current_approval, $user_id]);
        
        $is_approved = $current_approval;
        $role = $current_role;
    }

    // 5. Set session
    $_SESSION['user_id'] = $user_id;
    $_SESSION['email'] = $email;
    $_SESSION['name'] = $name;
    $_SESSION['role'] = $role;
    $_SESSION['is_approved'] = $is_approved;

    // Redirect based on approval status
    if ($is_approved) {
        header('Location: dashboard.php');
    } else {
        header('Location: pending_approval.php');
    }
    exit;

} catch (PDOException $e) {
    die("Database error: " . $e->getMessage());
}
