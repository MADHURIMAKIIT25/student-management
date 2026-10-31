'use strict';

(() => {
  const API = {
    login: '/api/auth/login',
    register: '/api/auth/register',
    account: '/api/account/me',
    students: '/api/students',
    search: '/api/students/search',
    myProfile: '/api/students/me'
  };

  const TOKEN_KEY = 'studentflow_access_token';
  const USER_KEY = 'studentflow_username';
  const state = {
    role: null,
    page: 0,
    size: 5,
    keyword: '',
    sortBy: 'id',
    direction: 'desc',
    totalPages: 1,
    totalElements: 0,
    students: [],
    loading: false,
    searchTimer: null,
    myProfile: null
  };

  const $ = (id) => document.getElementById(id);
  const getToken = () => sessionStorage.getItem(TOKEN_KEY);
  const getUsername = () => sessionStorage.getItem(USER_KEY) || 'User';
  const escapeHtml = (value) => String(value ?? '').replace(/[&<>"']/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char]);
  const initials = (name) => String(name || '?').trim().split(/\s+/).slice(0, 2).map((part) => part[0] || '').join('').toUpperCase() || '?';

  function showToast(message, type = 'success') {
    const region = $('toastRegion');
    if (!region) return;
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.textContent = message;
    region.appendChild(toast);
    window.setTimeout(() => toast.remove(), 3500);
  }

  async function request(url, options = {}) {
    const headers = new Headers(options.headers || {});
    if (options.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
    if (getToken()) headers.set('Authorization', `Bearer ${getToken()}`);

    let response;
    try {
      response = await fetch(url, { ...options, headers, credentials: 'same-origin' });
    } catch {
      throw new Error('Unable to reach the application server. Check that Spring Boot is running.');
    }

    const contentType = response.headers.get('content-type') || '';
    const raw = response.status === 204 ? '' : await response.text();
    let data = raw;
    if (raw && contentType.includes('application/json')) {
      try { data = JSON.parse(raw); } catch { data = raw; }
    }

    if (response.status === 401 && getToken()) {
      signOut(false);
      const error = new Error('Your session has expired. Please sign in again.');
      error.status = 401;
      throw error;
    }

    if (!response.ok) {
      const message = data && typeof data === 'object'
        ? (data.message || data.error || data.detail)
        : data;
      const error = new Error(message || `Request failed (${response.status}).`);
      error.status = response.status;
      throw error;
    }
    return data;
  }

  function parseToken(responseBody) {
    if (typeof responseBody === 'string') {
      const value = responseBody.trim();
      if (!value) return '';
      try {
        const parsed = JSON.parse(value);
        if (typeof parsed === 'string') return parsed.trim();
        if (parsed && typeof parsed.token === 'string') return parsed.token.trim();
      } catch { /* The API may return a raw JWT as text/plain. */ }
      return value.replace(/^"|"$/g, '');
    }
    if (responseBody && typeof responseBody.token === 'string') return responseBody.token.trim();
    return '';
  }

  function setVisible(viewId) {
    ['loginView', 'registerView', 'appView', 'studentView'].forEach((id) => {
      const node = $(id);
      if (node) node.hidden = id !== viewId;
    });
  }

  function showLogin(message = '', success = '') {
    setVisible('loginView');
    $('loginError').textContent = message;
    $('loginNotice').textContent = success;
    $('password').value = '';
    $('loginButton').disabled = false;
    $('loginButton').innerHTML = '<span>Sign in</span><span class="button-arrow">→</span>';
  }

  function showRegister() {
    setVisible('registerView');
    $('registerError').textContent = '';
    $('registerButton').disabled = false;
    $('registerButton').innerHTML = 'Create account <span class="button-arrow">→</span>';
  }

  function showAdminDashboard(account) {
    state.role = 'ADMIN';
    setVisible('appView');
    const username = account.username || getUsername();
    sessionStorage.setItem(USER_KEY, username);
    const avatar = initials(username);
    $('sidebarUsername').textContent = username;
    $('sidebarAvatar').textContent = avatar;
    $('topbarAvatar').textContent = avatar;
    $('greetingName').textContent = username;
    loadStudents();
  }

  async function showStudentDashboard(account) {
    state.role = 'STUDENT';
    setVisible('studentView');
    const username = account.username || getUsername();
    sessionStorage.setItem(USER_KEY, username);
    const avatar = initials(username);
    $('studentSidebarUsername').textContent = username;
    $('studentSidebarAvatar').textContent = avatar;
    $('studentTopbarAvatar').textContent = avatar;
    $('studentGreetingName').textContent = username;
    await loadMyProfile();
  }

  async function routeByRole(account) {
    if (account.role === 'ADMIN') {
      showAdminDashboard(account);
    } else if (account.role === 'STUDENT') {
      await showStudentDashboard(account);
    } else {
      signOut(false);
      showLogin('This account does not have a supported application role.');
    }
  }

  function signOut(showMessage = true) {
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(USER_KEY);
    state.role = null;
    state.myProfile = null;
    showLogin();
    if (showMessage) showToast('You have signed out.');
  }

  async function resolveSavedSession() {
    try {
      const account = await request(API.account);
      await routeByRole(account);
    } catch (error) {
      signOut(false);
      if (error.status !== 401) showLogin(error.message || 'Unable to restore your session. Please sign in again.');
    }
  }

  async function handleLogin(event) {
    event.preventDefault();
    $('loginError').textContent = '';
    $('loginNotice').textContent = '';

    const username = $('username').value.trim();
    const password = $('password').value;
    if (!username || !password) {
      $('loginError').textContent = 'Enter both your username and password.';
      return;
    }

    $('loginButton').disabled = true;
    $('loginButton').innerHTML = '<span class="spinner"></span><span>Signing in…</span>';
    try {
      const result = await request(API.login, {
        method: 'POST',
        body: JSON.stringify({ username, password })
      });
      const token = parseToken(result);
      if (!token) throw new Error('The server did not return an access token. Please try again.');

      sessionStorage.setItem(TOKEN_KEY, token);
      sessionStorage.setItem(USER_KEY, username);
      $('password').value = '';

      // Never trust a role supplied by the browser. Fetch it from the protected API.
      const account = await request(API.account);
      await routeByRole(account);
    } catch (error) {
      $('loginError').textContent = error.message || 'Sign-in failed. Check your credentials.';
      $('loginButton').disabled = false;
      $('loginButton').innerHTML = '<span>Sign in</span><span class="button-arrow">→</span>';
    }
  }

  function selectedRegistrationGender() {
    const selected = $('regGender').value;
    if (selected === 'custom') return $('regGenderCustom').value.trim();
    return selected || null;
  }

  async function handleRegistration(event) {
    event.preventDefault();
    $('registerError').textContent = '';

    const password = $('regPassword').value;
    const confirmPassword = $('regConfirmPassword').value;
    const ageText = $('regAge').value.trim();
    const gender = selectedRegistrationGender();

    if (password !== confirmPassword) {
      $('registerError').textContent = 'Passwords do not match.';
      $('regConfirmPassword').focus();
      return;
    }
    if (gender && gender.length > 30) {
      $('registerError').textContent = 'Gender description must not exceed 30 characters.';
      return;
    }
    if (ageText !== '' && (!Number.isInteger(Number(ageText)) || Number(ageText) < 0 || Number(ageText) > 130)) {
      $('registerError').textContent = 'Enter a valid age between 0 and 130, or leave it blank.';
      return;
    }

    const payload = {
      username: $('regUsername').value.trim(),
      password,
      confirmPassword,
      name: $('regName').value.trim(),
      email: $('regEmail').value.trim(),
      course: $('regCourse').value.trim(),
      gender,
      age: ageText === '' ? null : Number(ageText)
    };

    if (!$('registerForm').reportValidity()) return;
    $('registerButton').disabled = true;
    $('registerButton').textContent = 'Creating account…';

    try {
      await request(API.register, { method: 'POST', body: JSON.stringify(payload) });
      const registeredUsername = payload.username;
      $('registerForm').reset();
      showLogin('', 'Your student account has been created. Sign in to continue.');
      $('username').value = registeredUsername;
      $('password').focus();
    } catch (error) {
      $('registerError').textContent = error.message || 'Registration could not be completed.';
    } finally {
      $('registerButton').disabled = false;
      $('registerButton').innerHTML = 'Create account <span class="button-arrow">→</span>';
    }
  }

  function normalizePage(payload) {
    if (payload && Array.isArray(payload.content)) {
      const page = payload.page || {};
      return {
        content: payload.content,
        number: Number(page.number ?? state.page),
        totalPages: Math.max(1, Number(page.totalPages ?? 1)),
        totalElements: Number(page.totalElements ?? payload.content.length)
      };
    }
    if (Array.isArray(payload)) return { content: payload, number: 0, totalPages: 1, totalElements: payload.length };
    return { content: [], number: 0, totalPages: 1, totalElements: 0 };
  }

  async function loadStudents() {
    if (!getToken() || state.loading) return;
    state.loading = true;
    $('studentsTableBody').innerHTML = '<tr><td colspan="6" class="loading-cell"><span class="spinner"></span> Loading student records…</td></tr>';
    const params = new URLSearchParams({
      keyword: state.keyword,
      page: String(state.page),
      size: String(state.size),
      sortBy: state.sortBy,
      direction: state.direction
    });

    try {
      const payload = await request(`${API.search}?${params.toString()}`);
      const page = normalizePage(payload);
      state.students = page.content;
      state.page = page.number;
      state.totalPages = page.totalPages;
      state.totalElements = page.totalElements;
      renderStudents();
      updateSummary();
    } catch (error) {
      $('studentsTableBody').innerHTML = `<tr><td colspan="6" class="empty-cell"><div class="empty-state"><div class="empty-icon">!</div><strong>Couldn't load student records</strong><span>${escapeHtml(error.message)}</span></div></td></tr>`;
      $('tableSummary').textContent = 'Records could not be loaded';
      if (error.status !== 401) showToast(error.message, 'error');
    } finally {
      state.loading = false;
    }
  }

  function renderStudents() {
    if (!state.students.length) {
      const title = state.keyword ? 'No matching students' : 'No student records yet';
      const message = state.keyword ? 'Try a different name, email or course.' : 'Add your first student to get started.';
      $('studentsTableBody').innerHTML = `<tr><td colspan="6" class="empty-cell"><div class="empty-state"><div class="empty-icon">✦</div><strong>${title}</strong><span>${message}</span></div></td></tr>`;
    } else {
      $('studentsTableBody').innerHTML = state.students.map((student) => `
        <tr>
          <td><span class="student-id">#${escapeHtml(student.id)}</span></td>
          <td><div class="student-cell"><div class="student-avatar">${escapeHtml(initials(student.name))}</div><div><div class="student-name">${escapeHtml(student.name)}</div><div class="student-subline">Student record</div></div></div></td>
          <td><span class="email-cell">${escapeHtml(student.email)}</span></td>
          <td><span class="course-pill">${escapeHtml(student.course)}</span></td>
          <td><span class="age-cell">${student.age === null || student.age === undefined || student.age === '' ? '—' : escapeHtml(student.age)}</span></td>
          <td><div class="row-actions"><button class="row-action" type="button" data-action="edit" data-id="${escapeHtml(student.id)}" aria-label="Edit ${escapeHtml(student.name)}" title="Edit"><svg viewBox="0 0 24 24"><path d="m14 5 5 5M4 20l4.5-1 10.8-10.8a2.1 2.1 0 0 0-3-3L5.5 16 4 20Z"></path></svg></button><button class="row-action delete" type="button" data-action="delete" data-id="${escapeHtml(student.id)}" aria-label="Delete ${escapeHtml(student.name)}" title="Delete"><svg viewBox="0 0 24 24"><path d="M4 7h16M10 11v6M14 11v6M5.5 7l1 14h11l1-14M9 7V4h6v3"></path></svg></button></div></td>
        </tr>`).join('');
    }

    $('sidebarCount').textContent = String(state.totalElements);
    $('totalStudents').textContent = String(state.totalElements);
    $('pageStudents').textContent = String(state.students.length);
    const courses = new Set(state.students.map((student) => String(student.course || '').trim().toLowerCase()).filter(Boolean));
    $('courseCount').textContent = String(courses.size);
    const ages = state.students.map((student) => Number(student.age)).filter((age) => Number.isFinite(age) && age >= 0);
    $('averageAge').textContent = ages.length ? (ages.reduce((sum, age) => sum + age, 0) / ages.length).toFixed(1) : '—';
    $('previousPage').disabled = state.page <= 0;
    $('nextPage').disabled = state.page >= state.totalPages - 1;
    $('pageIndicator').textContent = `${state.page + 1} / ${state.totalPages}`;
  }

  function updateSummary() {
    const start = state.totalElements === 0 ? 0 : state.page * state.size + 1;
    const end = Math.min((state.page + 1) * state.size, state.totalElements);
    $('tableSummary').textContent = `Showing ${start}–${end} of ${state.totalElements} student${state.totalElements === 1 ? '' : 's'}`;
  }

  function openModal(student = null) {
    $('studentForm').reset();
    $('studentFormError').textContent = '';
    $('studentId').value = student ? student.id : '';
    $('modalKicker').textContent = student ? 'UPDATE RECORD' : 'NEW RECORD';
    $('modalTitle').textContent = student ? 'Edit student' : 'Add a student';
    $('studentName').value = student?.name ?? '';
    $('studentEmail').value = student?.email ?? '';
    $('studentCourse').value = student?.course ?? '';
    $('studentAge').value = student?.age ?? '';
    $('studentModal').hidden = false;
    window.setTimeout(() => $('studentName').focus(), 30);
  }

  function closeModal() {
    $('studentModal').hidden = true;
    $('studentFormError').textContent = '';
  }

  async function saveStudent(event) {
    event.preventDefault();
    $('studentFormError').textContent = '';
    const id = $('studentId').value;
    const name = $('studentName').value.trim();
    const email = $('studentEmail').value.trim();
    const course = $('studentCourse').value.trim();
    const ageText = $('studentAge').value.trim();

    if (name.length < 2 || !email || !course) {
      $('studentFormError').textContent = 'Enter a name (at least 2 characters), email and course.';
      return;
    }
    if (!$('studentEmail').checkValidity()) {
      $('studentFormError').textContent = 'Enter a valid email address.';
      return;
    }
    if (ageText !== '' && (!Number.isInteger(Number(ageText)) || Number(ageText) < 0)) {
      $('studentFormError').textContent = 'Age must be a whole number greater than or equal to zero.';
      return;
    }

    const student = { name, email, course, age: ageText === '' ? null : Number(ageText) };
    $('saveStudentButton').disabled = true;
    $('saveStudentButton').textContent = id ? 'Saving changes…' : 'Adding student…';
    try {
      await request(id ? `${API.students}/${encodeURIComponent(id)}` : API.students, {
        method: id ? 'PUT' : 'POST',
        body: JSON.stringify(student)
      });
      closeModal();
      showToast(id ? 'Student record updated.' : 'Student added successfully.');
      state.page = id ? state.page : 0;
      await loadStudents();
    } catch (error) {
      $('studentFormError').textContent = error.message;
    } finally {
      $('saveStudentButton').disabled = false;
      $('saveStudentButton').innerHTML = 'Save student <span>→</span>';
    }
  }

  async function deleteStudent(id) {
    const student = state.students.find((item) => String(item.id) === String(id));
    if (!student) return;
    if (!window.confirm(`Delete the student record for ${student.name}? This action cannot be undone.`)) return;
    try {
      await request(`${API.students}/${encodeURIComponent(id)}`, { method: 'DELETE' });
      showToast('Student record deleted.');
      if (state.students.length === 1 && state.page > 0) state.page -= 1;
      await loadStudents();
    } catch (error) {
      showToast(error.message, 'error');
    }
  }

  async function loadMyProfile() {
    $('profileLoading').hidden = false;
    $('profileLoading').textContent = 'Loading your profile…';
    $('myProfileForm').hidden = true;
    $('profileUnavailable').hidden = true;
    try {
      const profile = await request(API.myProfile);
      state.myProfile = profile;
      $('myName').value = profile.name || '';
      $('myEmail').value = profile.email || '';
      $('myCourse').value = profile.course || '';
      $('myAge').value = profile.age ?? '';
      const genderOptions = Array.from($('myGender').options).map((option) => option.value);
      if (profile.gender && !genderOptions.includes(profile.gender)) {
        $('myGender').value = 'custom';
        $('myGenderCustom').value = profile.gender;
        $('myGenderCustomWrap').hidden = false;
      } else {
        $('myGender').value = profile.gender || '';
        $('myGenderCustom').value = '';
        $('myGenderCustomWrap').hidden = true;
      }
      $('profileLoading').hidden = true;
      $('myProfileForm').hidden = false;
    } catch (error) {
      $('profileLoading').hidden = true;
      $('profileUnavailable').hidden = false;
      $('profileUnavailable').textContent = error.status === 404
        ? 'No student profile is linked to this account yet. Please contact the administrator to arrange your profile.'
        : (error.message || 'Your profile could not be loaded.');
    }
  }

  async function saveMyProfile(event) {
    event.preventDefault();
    $('myProfileError').textContent = '';
    const ageText = $('myAge').value.trim();
    let gender = $('myGender').value;
    if (gender === 'custom') gender = $('myGenderCustom').value.trim();
    if (gender && gender.length > 30) {
      $('myProfileError').textContent = 'Gender description must not exceed 30 characters.';
      return;
    }
    if (ageText !== '' && (!Number.isInteger(Number(ageText)) || Number(ageText) < 0 || Number(ageText) > 130)) {
      $('myProfileError').textContent = 'Enter a valid age between 0 and 130, or leave it blank.';
      return;
    }
    const payload = {
      name: $('myName').value.trim(),
      email: $('myEmail').value.trim(),
      course: $('myCourse').value.trim(),
      age: ageText === '' ? null : Number(ageText),
      gender: gender || null
    };
    if (!$('myProfileForm').reportValidity()) return;

    $('saveMyProfile').disabled = true;
    $('saveMyProfile').textContent = 'Saving profile…';
    try {
      const profile = await request(API.myProfile, { method: 'PUT', body: JSON.stringify(payload) });
      state.myProfile = profile;
      showToast('Your profile has been updated.');
    } catch (error) {
      $('myProfileError').textContent = error.message;
    } finally {
      $('saveMyProfile').disabled = false;
      $('saveMyProfile').innerHTML = 'Save profile <span>→</span>';
    }
  }

  function bindEvents() {
    $('loginForm').addEventListener('submit', handleLogin);
    $('registerForm').addEventListener('submit', handleRegistration);
    $('showRegister').addEventListener('click', showRegister);
    $('showLogin').addEventListener('click', () => showLogin());

    $('regGender').addEventListener('change', () => {
      $('regGenderCustomWrap').hidden = $('regGender').value !== 'custom';
      if ($('regGender').value !== 'custom') $('regGenderCustom').value = '';
    });
    $('myGender').addEventListener('change', () => {
      $('myGenderCustomWrap').hidden = $('myGender').value !== 'custom';
      if ($('myGender').value !== 'custom') $('myGenderCustom').value = '';
    });

    $('togglePassword').addEventListener('click', () => {
      const show = $('password').type === 'password';
      $('password').type = show ? 'text' : 'password';
      $('togglePassword').setAttribute('aria-label', show ? 'Hide password' : 'Show password');
    });

    $('logoutButton').addEventListener('click', () => signOut(true));
    $('studentLogoutButton').addEventListener('click', () => signOut(true));
    $('addStudentTop').addEventListener('click', () => openModal());
    $('addStudentInline').addEventListener('click', () => openModal());
    $('closeModal').addEventListener('click', closeModal);
    $('cancelModal').addEventListener('click', closeModal);
    $('studentModal').addEventListener('click', (event) => { if (event.target === $('studentModal')) closeModal(); });
    $('studentForm').addEventListener('submit', saveStudent);
    $('myProfileForm').addEventListener('submit', saveMyProfile);

    $('studentsTableBody').addEventListener('click', (event) => {
      const button = event.target.closest('button[data-action]');
      if (!button) return;
      const { action, id } = button.dataset;
      if (action === 'edit') {
        const student = state.students.find((item) => String(item.id) === String(id));
        if (student) openModal(student);
      } else if (action === 'delete') {
        deleteStudent(id);
      }
    });

    $('searchInput').addEventListener('input', () => {
      window.clearTimeout(state.searchTimer);
      state.searchTimer = window.setTimeout(() => {
        state.keyword = $('searchInput').value.trim();
        state.page = 0;
        loadStudents();
      }, 300);
    });
    $('sortSelect').addEventListener('change', () => {
      const [sortBy, direction] = $('sortSelect').value.split(':');
      state.sortBy = sortBy;
      state.direction = direction;
      state.page = 0;
      loadStudents();
    });
    $('pageSize').addEventListener('change', () => {
      state.size = Number($('pageSize').value);
      state.page = 0;
      loadStudents();
    });
    $('previousPage').addEventListener('click', () => {
      if (state.page > 0) { state.page -= 1; loadStudents(); }
    });
    $('nextPage').addEventListener('click', () => {
      if (state.page < state.totalPages - 1) { state.page += 1; loadStudents(); }
    });
    $('refreshButton').addEventListener('click', loadStudents);
    $('dashboardNav').addEventListener('click', (event) => { event.preventDefault(); $('studentsSection').scrollIntoView({ behavior: 'smooth' }); });
    $('studentsNav').addEventListener('click', (event) => { event.preventDefault(); $('studentsSection').scrollIntoView({ behavior: 'smooth' }); });
    $('menuButton').addEventListener('click', () => {
      const sidebar = $('sidebar');
      const isOpen = sidebar.classList.toggle('open');
      let backdrop = $('sidebarBackdrop');
      if (isOpen && !backdrop) {
        backdrop = document.createElement('div');
        backdrop.id = 'sidebarBackdrop';
        backdrop.className = 'sidebar-backdrop';
        backdrop.addEventListener('click', () => { sidebar.classList.remove('open'); backdrop.remove(); });
        document.body.appendChild(backdrop);
      } else if (!isOpen && backdrop) {
        backdrop.remove();
      }
    });
    document.addEventListener('keydown', (event) => {
      if (event.key === 'Escape' && !$('studentModal').hidden) closeModal();
    });
  }

  function init() {
    const year = new Date().getFullYear();
    $('year').textContent = year;
    $('appYear').textContent = year;
    $('studentAppYear').textContent = year;
    const today = new Intl.DateTimeFormat(undefined, { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric' }).format(new Date()).toUpperCase();
    $('todayDate').textContent = today;
    $('studentTodayDate').textContent = today;
    bindEvents();
    if (getToken()) resolveSavedSession();
    else showLogin();
  }

  init();
})();
