/**
 * JTrac Three-State Theme Switcher (☀️ Light -> 🌙 Dark -> 🖥️ System)
 */
(function() {
    function getStoredTheme() {
        try {
            return localStorage.getItem('jtrac-theme') || 'system';
        } catch(e) {
            return 'system';
        }
    }

    function isSystemDark() {
        try {
            return !!(window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches);
        } catch(e) {
            return false;
        }
    }

    function getThemeIcon(mode) {
        if (mode === 'light') return '☀️';
        if (mode === 'dark') return '🌙';
        return '🖥️';
    }

    function updateThemeUI(mode) {
        var icon = getThemeIcon(mode);
        var iconEls = document.querySelectorAll('.theme-icon');
        for (var i = 0; i < iconEls.length; i++) {
            iconEls[i].textContent = icon;
        }
    }

    function applyTheme(mode) {
        var d = document.documentElement;
        var effectiveTheme = mode;
        if (mode === 'system') {
            effectiveTheme = isSystemDark() ? 'dark' : 'light';
        }
        d.setAttribute('data-theme', effectiveTheme);
        d.setAttribute('data-theme-mode', mode);
        updateThemeUI(mode);
    }

    // 1. Initial application on script load
    var initialMode = getStoredTheme();
    applyTheme(initialMode);

    // 2. Global Toggle Function
    window.jtracToggleTheme = function(e) {
        if (e) {
            if (e.preventDefault) e.preventDefault();
            if (e.stopPropagation) e.stopPropagation();
        }
        var d = document.documentElement;
        var currentMode = d.getAttribute('data-theme-mode') || 'system';
        var nextMode;
        if (currentMode === 'light') {
            nextMode = 'dark';
        } else if (currentMode === 'dark') {
            nextMode = 'system';
        } else {
            nextMode = 'light';
        }

        try {
            localStorage.setItem('jtrac-theme', nextMode);
        } catch(err) {
            // Gracefully handle private browsing / quota errors
        }

        applyTheme(nextMode);
        return false;
    };

    // 3. React to OS system dark mode changes when mode is 'system'
    if (window.matchMedia) {
        try {
            window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', function(e) {
                var d = document.documentElement;
                if (d.getAttribute('data-theme-mode') === 'system') {
                    d.setAttribute('data-theme', e.matches ? 'dark' : 'light');
                }
            });
        } catch(e) {}
    }

    // 4. Font Scale Switcher (A -> A+ -> A++)
    function getStoredFontScale() {
        try {
            return localStorage.getItem('jtrac-font-scale') || 'normal';
        } catch(e) {
            return 'normal';
        }
    }

    function getFontScaleIcon(scale) {
        if (scale === 'xlarge') return 'A+++';
        if (scale === 'large') return 'A++';
        if (scale === 'medium') return 'A+';
        return 'A';
    }

    function updateFontScaleUI(scale) {
        var label = getFontScaleIcon(scale);
        var els = document.querySelectorAll('.font-scale-icon');
        for (var i = 0; i < els.length; i++) {
            els[i].textContent = label;
        }
    }

    function applyFontScale(scale) {
        document.documentElement.setAttribute('data-font-scale', scale);
        updateFontScaleUI(scale);
    }

    var initialFontScale = getStoredFontScale();
    applyFontScale(initialFontScale);

    window.jtracToggleFontScale = function(e) {
        if (e) {
            if (e.preventDefault) e.preventDefault();
            if (e.stopPropagation) e.stopPropagation();
        }
        var d = document.documentElement;
        var current = d.getAttribute('data-font-scale') || 'normal';
        var next;
        if (current === 'normal') {
            next = 'medium';
        } else if (current === 'medium') {
            next = 'large';
        } else if (current === 'large') {
            next = 'xlarge';
        } else {
            next = 'normal';
        }

        try {
            localStorage.setItem('jtrac-font-scale', next);
        } catch(err) {}

        applyFontScale(next);
        return false;
    };

    // 5. Ensure icons are synced when DOM is ready
    function syncOnReady() {
        var currentMode = document.documentElement.getAttribute('data-theme-mode') || getStoredTheme();
        updateThemeUI(currentMode);

        var currentScale = document.documentElement.getAttribute('data-font-scale') || getStoredFontScale();
        updateFontScaleUI(currentScale);

        // Bind touch/click listeners to buttons as fallback
        var btns = document.querySelectorAll('.mobile-theme-btn, .desktop-theme-btn, .login-theme-btn, .nav-tab-theme');
        for (var i = 0; i < btns.length; i++) {
            btns[i].onclick = window.jtracToggleTheme;
        }

        var fontBtns = document.querySelectorAll('.mobile-font-btn, .desktop-font-btn, .login-font-btn, .nav-tab-font');
        for (var j = 0; j < fontBtns.length; j++) {
            fontBtns[j].onclick = window.jtracToggleFontScale;
        }
    }

    // 5. Mobile History Detail Bottom Sheet Modal
    window.jtracOpenHistoryModal = function(row) {
        var overlay = document.getElementById('history-modal-overlay');
        if (!overlay || !row) return;

        // TimeStamp
        var timeEl = row.querySelector('.col-hist-timeStamp');
        var timeText = timeEl ? timeEl.textContent.trim() : '';
        var modalTime = document.getElementById('modal-time');
        if (modalTime) modalTime.textContent = timeText;

        // Status
        var statusEl = row.querySelector('.col-hist-status');
        var statusVal = statusEl ? statusEl.textContent.trim() : '';
        var modalStatus = document.getElementById('modal-status');
        if (modalStatus) modalStatus.textContent = statusVal || '-';

        // Logged By
        var loggedByEl = row.querySelector('.col-hist-loggedBy');
        var loggedByVal = loggedByEl ? loggedByEl.textContent.trim() : '';
        var modalLoggedBy = document.getElementById('modal-loggedBy');
        if (modalLoggedBy) modalLoggedBy.textContent = loggedByVal || '-';

        // Assigned To
        var assignedToEl = row.querySelector('.col-hist-assignedTo');
        var assignedToVal = assignedToEl ? assignedToEl.textContent.trim() : '';
        var modalAssignedTo = document.getElementById('modal-assignedTo');
        if (modalAssignedTo) modalAssignedTo.textContent = assignedToVal || '-';

        // Custom Fields
        var fieldCells = row.querySelectorAll('.col-hist-fields');
        var customContainer = document.getElementById('modal-custom-fields');
        var customSection = document.getElementById('modal-custom-fields-section');
        if (customContainer) {
            customContainer.innerHTML = '';
            var hasCustom = false;
            for (var i = 0; i < fieldCells.length; i++) {
                var cell = fieldCells[i];
                var label = cell.getAttribute('data-label') || '';
                var val = cell.textContent ? cell.textContent.trim() : '';
                if (val && val !== '' && val !== '-') {
                    hasCustom = true;
                    var itemDiv = document.createElement('div');
                    itemDiv.className = 'history-modal-item';
                    var lblSpan = document.createElement('span');
                    lblSpan.className = 'history-modal-label';
                    lblSpan.textContent = label;
                    var valSpan = document.createElement('span');
                    valSpan.className = 'history-modal-val';
                    valSpan.textContent = val;
                    itemDiv.appendChild(lblSpan);
                    itemDiv.appendChild(valSpan);
                    customContainer.appendChild(itemDiv);
                }
            }
            if (customSection) {
                customSection.style.display = hasCustom ? 'block' : 'none';
            }
        }

        // Comment & Attachments
        var commentCell = row.querySelector('.col-hist-comment');
        var modalComment = document.getElementById('modal-comment');
        if (modalComment && commentCell) {
            var clone = commentCell.cloneNode(true);
            var btn = clone.querySelector('.history-detail-btn');
            if (btn && btn.parentNode) {
                btn.parentNode.removeChild(btn);
            }
            var textContent = clone.textContent ? clone.textContent.trim() : '';
            var hasLinksOrImages = clone.querySelector('a, img');
            if (!textContent && !hasLinksOrImages) {
                modalComment.innerHTML = '<span style="color:var(--text-muted);font-style:italic;">-</span>';
            } else {
                modalComment.innerHTML = clone.innerHTML;
            }
        }

        // Show Modal
        overlay.style.display = 'flex';
        overlay.classList.add('open');
        document.body.style.overflow = 'hidden';
    };

    window.jtracCloseHistoryModal = function(e) {
        if (e && e.target && e.target !== document.getElementById('history-modal-overlay') && !e.target.classList.contains('history-modal-close')) {
            return;
        }
        var overlay = document.getElementById('history-modal-overlay');
        if (overlay) {
            overlay.style.display = 'none';
            overlay.classList.remove('open');
        }
        document.body.style.overflow = '';
    };

    // Close on Escape key
    document.addEventListener('keydown', function(e) {
        if (e.key === 'Escape' || e.keyCode === 27) {
            var overlay = document.getElementById('history-modal-overlay');
            if (overlay && (overlay.classList.contains('open') || overlay.style.display !== 'none')) {
                jtracCloseHistoryModal();
            }
            var searchDrawer = document.getElementById('mobile-search-drawer');
            if (searchDrawer && searchDrawer.classList.contains('open')) {
                searchDrawer.classList.remove('open');
            }
            if (window.jtracHideSearchLoading) {
                window.jtracHideSearchLoading();
            }
        }
    });

    // 6. Search & Item List Reload Loading Modal Controller (150ms anti-flicker delay + 15s safety timeout)
    var searchLoadingDelayTimer = null;
    var searchLoadingSafetyTimer = null;

    window.jtracHideSearchLoading = function() {
        if (searchLoadingDelayTimer) {
            clearTimeout(searchLoadingDelayTimer);
            searchLoadingDelayTimer = null;
        }
        if (searchLoadingSafetyTimer) {
            clearTimeout(searchLoadingSafetyTimer);
            searchLoadingSafetyTimer = null;
        }
        var overlay = document.getElementById('jtrac-search-loading-overlay');
        if (overlay) {
            overlay.classList.remove('active');
            overlay.setAttribute('aria-hidden', 'true');
        }
    };

    window.jtracShowSearchLoading = function() {
        window.jtracHideSearchLoading();
        searchLoadingDelayTimer = setTimeout(function() {
            var overlay = document.getElementById('jtrac-search-loading-overlay');
            if (overlay) {
                overlay.classList.add('active');
                overlay.setAttribute('aria-hidden', 'false');
            }
        }, 150);
        searchLoadingSafetyTimer = setTimeout(function() {
            window.jtracHideSearchLoading();
        }, 15000);
    };

    function closestElement(el, selector) {
        while (el && el.nodeType === 1) {
            if (el.matches ? el.matches(selector) : (el.msMatchesSelector && el.msMatchesSelector(selector))) {
                return el;
            }
            el = el.parentElement || el.parentNode;
        }
        return null;
    }

    document.addEventListener('submit', function(e) {
        if (e.defaultPrevented) return;
        var form = e.target;
        if (!form || form.tagName !== 'FORM') return;

        var isQuickSearch = form.classList.contains('desktop-search-form') || form.classList.contains('mobile-search-form');
        if (isQuickSearch) {
            var input = form.querySelector('input[type="text"]');
            var val = input && input.value ? input.value.replace(/^\s+|\s+$/g, '') : '';
            if (!val) {
                if (e.preventDefault) e.preventDefault();
                return;
            }
            window.jtracShowSearchLoading();
            return;
        }

        var isFilterForm = form.classList.contains('item-list-filter-form');
        var isAdvancedSearchForm = !!form.querySelector('.item-search-unified-bar');
        if (isFilterForm || isAdvancedSearchForm) {
            window.jtracShowSearchLoading();
        }
    }, false);

    document.addEventListener('click', function(e) {
        if (e.defaultPrevented) return;
        if (typeof e.button === 'number' && e.button !== 0) return;
        if (e.ctrlKey || e.metaKey || e.shiftKey || e.altKey) return;

        var link = closestElement(e.target, 'a');
        if (!link) return;
        var href = link.getAttribute('href');
        if (!href || href.indexOf('javascript:') === 0 || href === '#') return;
        if (link.getAttribute('target') === '_blank') return;

        var isPaginationLink = !!closestElement(link, '.page-links');
        var isSortableHeaderLink = !!closestElement(link, 'table.jtrac-list tr.sortable th');
        var isClearSearchLink = link.classList.contains('search-keyword-clear');
        var isToolbarCountLink = !!closestElement(link, '.item-list-toolbar-left');

        if (isPaginationLink || isSortableHeaderLink || isClearSearchLink || isToolbarCountLink) {
            window.jtracShowSearchLoading();
        }
    }, false);

    window.addEventListener('pageshow', function() {
        window.jtracHideSearchLoading();
    });

    window.jtracToggleMobileSearch = function(e) {
        if (e) {
            if (e.preventDefault) e.preventDefault();
            if (e.stopPropagation) e.stopPropagation();
        }
        var drawer = document.getElementById('mobile-search-drawer');
        if (!drawer) return false;
        var isOpen = drawer.classList.contains('open');
        if (isOpen) {
            drawer.classList.remove('open');
        } else {
            drawer.classList.add('open');
            var input = drawer.querySelector('input[type="text"]');
            if (input) {
                setTimeout(function() {
                    input.focus();
                }, 100);
            }
        }
        return false;
    };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', syncOnReady);
    } else {
        syncOnReady();
    }
})();
