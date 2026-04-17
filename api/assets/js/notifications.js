class AlertManager {
    constructor() {
        this.pollInterval = 3000;
        this.alertSound = new Audio('sound.mp3');
        this.isAudioUnlocked = false;
        this.currentReminderHash = null;
        this.init();
    }

    init() {
        console.log("Alert Manager Active.");

        const testBtn = document.getElementById('testSound');
        if (testBtn) {
            testBtn.addEventListener('click', () => {
                this.unlockAndPlayTest();
            });
        }

        this.startPolling();
        
        // Fail-safe: Any click on the document can help unlock if not already done
        document.addEventListener('click', () => {
            if (!this.isAudioUnlocked) this.unlockAndPlayTest();
        }, { once: true });
    }



    unlockAndPlayTest() {
        this.alertSound.volume = 1.0;
        this.alertSound.currentTime = 0;
        
        const testBtn = document.getElementById('testSound');
        if (testBtn) testBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Testing...';

        this.alertSound.play()
            .then(() => {
                console.log("Audio engine unlocked.");
                this.isAudioUnlocked = true;
                if (testBtn) {
                    testBtn.innerHTML = '<i class="fas fa-check"></i> Sound OK';
                    testBtn.classList.remove('btn-outline');
                    testBtn.classList.add('btn-primary');
                    // Reset after 2 seconds
                    setTimeout(() => {
                        testBtn.innerHTML = '<i class="fas fa-volume-up"></i> Test Sound';
                        testBtn.classList.remove('btn-primary');
                        testBtn.classList.add('btn-outline');
                    }, 2000);
                }
            })
            .catch(error => {
                console.warn("Audio interaction needed:", error);
                this.isAudioUnlocked = false;
                if (testBtn) {
                    testBtn.innerHTML = '<i class="fas fa-exclamation-triangle"></i> Tap to Enable Sound';
                    testBtn.style.borderColor = 'var(--accent)';
                    testBtn.style.color = 'var(--accent)';
                }
            });
    }

    startPolling() {
        this.checkReminders();
        setInterval(() => this.checkReminders(), this.pollInterval);
    }

    async checkReminders() {
        try {
            const response = await fetch('api/reminders.php?poll=1');
            const data = await response.json();

            if (data.session_sync && (data.session_sync.role_changed || data.session_sync.status_changed || data.session_sync.account_deleted)) {
                window.location.href = 'index.php?error=' + (data.session_sync.account_deleted ? 'account_deleted' : 'session_sync');
                return;
            }

            if (Array.isArray(data.notifications) && data.notifications.length > 0) {
                data.notifications.forEach(reminder => {
                    this.triggerRichAlert(reminder);
                });
            }

            if (data.ui_hash && data.ui_hash !== this.currentReminderHash) {
                if (this.currentReminderHash !== null && window.location.pathname.includes('dashboard.php')) {
                    this.refreshDashboardList();
                }
                this.currentReminderHash = data.ui_hash;
            }

        } catch (error) {
            console.error("Alert Engine Error:", error);
        }
    }

    refreshDashboardList() {
        const activeEl = document.activeElement;
        const isInput = activeEl.tagName === 'INPUT' || activeEl.tagName === 'TEXTAREA';
        if (!isInput) window.location.reload();
    }

    triggerRichAlert(reminder) {
        // Try audio regardless of what we think the status is
        this.alertSound.currentTime = 0;
        this.alertSound.volume = 1.0;
        this.alertSound.play().catch(() => {
            console.warn("Audio blocked by browser policy. User interaction required.");
            this.isAudioUnlocked = false;
            const testBtn = document.getElementById('testSound');
            if (testBtn) {
                testBtn.innerHTML = '<i class="fas fa-volume-mute"></i> Sound Blocked - Tap Me';
                testBtn.style.color = 'var(--accent)';
                testBtn.style.borderColor = 'var(--accent)';
            }
        });

        if ("vibrate" in navigator) navigator.vibrate([300, 100, 300]);

        if (Notification.permission === "granted") {
            const options = {
                body: `[${reminder.creator_name}] ${reminder.description || 'No additional details.'}`,
                icon: 'https://cdn-icons-png.flaticon.com/512/3119/3119338.png', 
                vibrate: [300, 100, 300],
                tag: `marv-remind-${reminder.id}`,
                requireInteraction: true
            };

            const titlePrefix = parseInt(reminder.is_global) === 1 ? '📢 GLOBAL ALERT: ' : 'Reminder: ';
            const notification = new Notification(`${titlePrefix}${reminder.title}`, options);
            notification.onclick = () => { window.focus(); notification.close(); };
        }
    }
}

const alertEngine = new AlertManager();
