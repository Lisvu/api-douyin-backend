// ====================================================
// DOUYIN LITE - CORE JAVASCRIPT CONTROLLER
// ====================================================

const BASE_URL = window.location.origin;

// State Management
let currentUser = null;
let token = localStorage.getItem('douyin_token') || null;

// Recommendation Carousel State
let recommendedVideos = [];
let activeVideoIndex = -1;
let lastScrollTime = 0;
let selectedPlaybackRate = 1;
const PLAYBACK_SPEED_OPTIONS = [0.5, 1, 1.25, 1.5, 2];

// Personal Videos State
let myVideosPage = 1;
const myVideosLimit = 6;

// Admin Monitoring Polling
let monitorIntervalId = null;

// DOMContentLoaded Entrypoint
document.addEventListener('DOMContentLoaded', () => {
  if (token) {
    // Parse user from saved info or token check
    try {
      const savedUser = localStorage.getItem('douyin_user');
      if (savedUser) {
        currentUser = JSON.parse(savedUser);
        initializeSession();
      } else {
        handleLogout();
      }
    } catch (e) {
      handleLogout();
    }
  } else {
    showAuthOverlay(true);
  }

  // Setup Keyboard Navigation for vertical slide recommends
  document.addEventListener('keydown', handleKeyboardNavigation);

  // Setup Scroll Wheel Navigation inside recommending zone
  const feedContainer = document.getElementById('feed-player-container');
  feedContainer.addEventListener('wheel', handleWheelNavigation, { passive: false });
});

// ----------------------------------------------------
// SESSION & AUTHENTICATION FUNCTIONS
// ----------------------------------------------------

function showAuthOverlay(show) {
  const overlay = document.getElementById('auth-overlay');
  if (show) {
    overlay.classList.add('active');
  } else {
    overlay.classList.remove('active');
  }
}

function switchAuthTab(tab) {
  const loginTab = document.getElementById('tab-login');
  const registerTab = document.getElementById('tab-register');
  const loginForm = document.getElementById('login-form');
  const registerForm = document.getElementById('register-form');

  if (tab === 'login') {
    loginTab.classList.add('active');
    registerTab.classList.remove('active');
    loginForm.classList.add('active');
    registerForm.classList.remove('active');
  } else {
    loginTab.classList.remove('active');
    registerTab.classList.add('active');
    loginForm.classList.remove('active');
    registerForm.classList.add('active');
  }
}

async function handleAuth(event, type) {
  event.preventDefault();
  
  const usernameInput = document.getElementById(`${type}-username`).value.trim();
  const passwordInput = document.getElementById(`${type}-password`).value.trim();

  if (!usernameInput || !passwordInput) {
    showToast('请输入完整的用户名和密码！', 'warning');
    return;
  }

  const endpoint = type === 'login' ? '/api/login' : '/api/register';
  
  try {
    const res = await fetch(BASE_URL + endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ username: usernameInput, password: passwordInput })
    });

    const data = await res.json();

    if (data.success) {
      token = data.token;
      currentUser = data.user;
      
      localStorage.setItem('douyin_token', token);
      localStorage.setItem('douyin_user', JSON.stringify(currentUser));
      
      showToast(data.message, 'success');
      showAuthOverlay(false);
      
      // Clear forms
      document.getElementById(`${type}-username`).value = '';
      document.getElementById(`${type}-password`).value = '';
      
      initializeSession();
    } else {
      showToast(data.message || '操作失败！', 'error');
    }
  } catch (err) {
    showToast('无法连接到服务器，请检查网络！', 'error');
    console.error(err);
  }
}

function initializeSession() {
  document.getElementById('display-username').innerText = currentUser.username;
  showAuthOverlay(false);
  
  // Load Default Recommend Screen
  switchSection('recommend');
}

function handleLogout() {
  // Clear State
  token = null;
  currentUser = null;
  localStorage.removeItem('douyin_token');
  localStorage.removeItem('douyin_user');
  
  // Pause any playing videos
  pauseAllVideos();
  
  // Reset navigation UI
  document.getElementById('display-username').innerText = 'Not Logged In';
  
  // Stop monitoring intervals
  if (monitorIntervalId) {
    clearInterval(monitorIntervalId);
    monitorIntervalId = null;
  }
  
  // Display Login Overlay
  showAuthOverlay(true);
  switchAuthTab('login');
  showToast('您已安全退出登录！', 'info');
}

function confirmAccountDeletion() {
  if (confirm('⚠️ 警告：您确定要注销(取消)当前账号吗？此操作不可逆，将永久删除您的账户以及所有您上传发布的视频作品！')) {
    executeAccountDeletion();
  }
}

async function executeAccountDeletion() {
  try {
    const res = await fetch(BASE_URL + '/api/users/delete', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`
      }
    });

    const data = await res.json();

    if (data.success) {
      showToast(data.message, 'success');
      
      // Clean up session and redirect to login screen
      token = null;
      currentUser = null;
      localStorage.removeItem('douyin_token');
      localStorage.removeItem('douyin_user');
      showAuthOverlay(true);
    } else {
      showToast(data.message || '注销账号失败！', 'error');
    }
  } catch (err) {
    showToast('无法连接到服务器！', 'error');
    console.error(err);
  }
}

function toggleSettingsMenu() {
  const menu = document.getElementById('settings-menu');
  menu.classList.toggle('active');
}

// Close settings menu if clicking elsewhere
window.addEventListener('click', (e) => {
  if (!e.target.closest('.user-actions-dropdown')) {
    const menu = document.getElementById('settings-menu');
    if (menu) menu.classList.remove('active');
  }
});

// ----------------------------------------------------
// ROUTING & APP VIEW MANAGER
// ----------------------------------------------------

function switchSection(sectionId) {
  // Hide all sections
  const sections = document.querySelectorAll('.content-section');
  sections.forEach(s => s.classList.remove('active'));

  // Deactivate all sidebar items
  const navItems = document.querySelectorAll('.nav-item');
  navItems.forEach(n => n.classList.remove('active'));

  // Activate target
  document.getElementById(`section-${sectionId}`).classList.add('active');
  document.getElementById(`nav-${sectionId}`).classList.add('active');

  // Pause active recommending videos if navigating away from "recommend"
  if (sectionId !== 'recommend') {
    pauseActiveRecommendVideo();
  }

  // Stop polling stats if navigating away from "monitor"
  if (sectionId !== 'monitor' && monitorIntervalId) {
    clearInterval(monitorIntervalId);
    monitorIntervalId = null;
  }

  // Update Page Header
  const title = document.getElementById('page-title');
  const desc = document.getElementById('page-desc');

  if (sectionId === 'recommend') {
    title.innerHTML = '<i class="fa-solid fa-compass"></i> 视频推荐流';
    desc.innerText = '按全网点赞数最高的顺序为您个性化推荐高清短视频';
    fetchRecommendations();
  } else if (sectionId === 'my-videos') {
    title.innerHTML = '<i class="fa-solid fa-video"></i> 我的视频作品管理';
    desc.innerText = '管理及发布您自己的短视频作品（支持分页和删除控制）';
    myVideosPage = 1;
    fetchMyVideos(myVideosPage);
  } else if (sectionId === 'monitor') {
    title.innerHTML = '<i class="fa-solid fa-chart-line"></i> 开发者系统实时监控';
    desc.innerText = '包含对所有网络接口的实时响应耗时监控，以及用户请求参数与返回负载记录';
    fetchSystemStatsAndLogs();
    
    // Start live polling every 3 seconds
    if (!monitorIntervalId) {
      monitorIntervalId = setInterval(fetchSystemStatsAndLogs, 3000);
    }
  }
}

// ----------------------------------------------------
// RECOMMEND VIDEOS CONTROLLER (Carousel & Swipe Logic)
// ----------------------------------------------------

async function fetchRecommendations() {
  const container = document.getElementById('feed-player-container');
  container.innerHTML = `
    <div class="empty-state-loader">
      <div class="spinner"></div>
      <p>加载推荐视频中...</p>
    </div>
  `;

  try {
    const res = await fetch(BASE_URL + '/api/videos/recommend', {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`
      }
    });

    const data = await res.json();

    if (data.success) {
      recommendedVideos = data.videos;
      activeVideoIndex = -1;
      
      renderRecommendCarousel();
    } else {
      showToast(data.message || '加载推荐视频失败！', 'error');
    }
  } catch (err) {
    showToast('加载失败，请检查后端服务！', 'error');
    console.error(err);
  }
}

function renderRecommendCarousel() {
  const container = document.getElementById('feed-player-container');
  container.innerHTML = '';

  if (recommendedVideos.length === 0) {
    container.innerHTML = `
      <div class="no-more-videos">
        <i class="fa-solid fa-face-smile-wink"></i>
        <h3>您已刷完所有的视频！</h3>
        <p>访问过的视频不再推荐，这代表我们的推荐排重系统正在完美运行。<br>您可以点击下方按钮重置您的观看历史记录，重新体验推荐。</p>
        <button class="btn btn-primary" onclick="resetWatchHistory()">
          <i class="fa-solid fa-rotate-left"></i> 重置观看历史记录
        </button>
      </div>
    `;
    return;
  }

  // Render each video slide
  recommendedVideos.forEach((video, index) => {
    const slide = document.createElement('div');
    slide.className = `video-slide ${index === 0 ? 'active' : 'next'}`;
    slide.id = `slide-video-${index}`;

    // Sleek HSL dynamic cover gradients if URL is placeholders
    const coverImageHtml = video.cover_url 
      ? `<img src="${video.cover_url}" style="position:absolute; width:100%; height:100%; object-fit:cover; filter:blur(10px); opacity:0.3; z-index:0;" alt="Cover Blur">` 
      : '';

    slide.innerHTML = `
      ${coverImageHtml}
      
      <!-- Video Player -->
      <video id="player-${index}" 
             src="${video.video_url}" 
             loop 
             preload="auto"
             playsinline
             onclick="togglePlayPause(${index})"
             onloadedmetadata="updateVideoProgress(${index})"
             ontimeupdate="updateVideoProgress(${index})">
      </video>

      <!-- Visual Overlays -->
      <div class="video-overlay-gradient-top"></div>
      <div class="video-overlay-gradient-bottom"></div>

      <!-- Play Pause Indicator Icon Overlay -->
      <div id="play-indicator-${index}" class="video-play-indicator">
        <i class="fa-solid fa-play"></i>
      </div>

      <!-- Bottom Controls -->
      <div class="video-bottom-controls" onclick="event.stopPropagation()">
        <div class="video-progress-track" onclick="seekVideo(event, ${index})">
          <div id="progress-${index}" class="video-progress-line"></div>
        </div>
        <div class="video-controls-row">
          <span id="time-${index}" class="video-time-label">00:00 / 00:00</span>
          <label class="video-speed-control" for="speed-${index}">
            <span>倍速</span>
            <select id="speed-${index}"
                    class="video-speed-select"
                    onclick="event.stopPropagation()"
                    onchange="changeVideoSpeed(${index}, this.value)">
              ${renderPlaybackSpeedOptions()}
            </select>
          </label>
        </div>
      </div>

      <!-- Video Meta details (Bottom Left) -->
      <div class="video-details">
        <div class="creator-name">${video.creator_name || '抖音创作者'}</div>
        <div class="video-title">${video.title}</div>
        <div class="video-desc">${video.description || ''}</div>
      </div>

      <!-- Interactive Actions Panel (Right Sidebar overlay) -->
      <div class="video-actions-sidebar">
        <!-- Creator Avatar -->
        <div class="action-creator-avatar">
          <i class="fa-solid fa-user-ninja"></i>
        </div>

        <!-- Like Heart Action -->
        <div class="action-btn-wrapper">
          <button id="like-btn-${index}" 
                  class="action-circle-btn ${video.is_liked ? 'liked' : ''}" 
                  onclick="toggleLikeVideo(${video.id}, ${index})">
            <i class="fa-solid fa-heart"></i>
          </button>
          <span id="like-count-${index}" class="action-label">${video.likes_count}</span>
        </div>

        <!-- Share Visual Decoration -->
        <div class="action-btn-wrapper">
          <button class="action-circle-btn" onclick="showToast('分享链接已复制！(Mock)', 'success')">
            <i class="fa-solid fa-share"></i>
          </button>
          <span class="action-label">分享</span>
        </div>
      </div>
    `;

    container.appendChild(slide);
  });

  // Play the first video in recommendation list
  setActiveVideo(0);
}

function setActiveVideo(index) {
  if (index < 0 || index >= recommendedVideos.length) return;
  
  // Pause current playing video
  if (activeVideoIndex !== -1 && activeVideoIndex !== index) {
    const prevPlayer = document.getElementById(`player-${activeVideoIndex}`);
    if (prevPlayer) {
      prevPlayer.pause();
      prevPlayer.currentTime = 0;
      updateVideoProgress(activeVideoIndex);
    }
    
    // Manage class names for smooth slide animations
    const prevSlide = document.getElementById(`slide-video-${activeVideoIndex}`);
    if (prevSlide) {
      prevSlide.classList.remove('active');
      prevSlide.classList.add(index > activeVideoIndex ? 'prev' : 'next');
    }
  }

  activeVideoIndex = index;
  
  // Update targets
  recommendedVideos.forEach((v, idx) => {
    const slide = document.getElementById(`slide-video-${idx}`);
    if (!slide) return;
    
    slide.classList.remove('active', 'prev', 'next');
    if (idx === index) {
      slide.classList.add('active');
    } else if (idx < index) {
      slide.classList.add('prev');
    } else {
      slide.classList.add('next');
    }
  });

  // Play newly active video player
  const activePlayer = document.getElementById(`player-${index}`);
  if (activePlayer) {
    activePlayer.playbackRate = selectedPlaybackRate;
    activePlayer.play().catch(err => {
      console.log("Auto-play blocked by browser. User interaction needed to start audio.", err);
      // Show play indicator initially to prompt user
      const indicator = document.getElementById(`play-indicator-${index}`);
      if (indicator) indicator.classList.add('active');
    });

    syncPlaybackRateControl(index);
    updateVideoProgress(index);
    
    // Trigger "Viewed" history record after sliding to it to support recommendation exclusions!
    markVideoAsWatched(recommendedVideos[index].id);
  }
}

async function markVideoAsWatched(videoId) {
  try {
    await fetch(`${BASE_URL}/api/videos/${videoId}/view`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`
      }
    });
    // Quietly complete view record in backend
  } catch (err) {
    console.error('Failed to report video view event:', err);
  }
}

function slidePrevious() {
  if (activeVideoIndex > 0) {
    setActiveVideo(activeVideoIndex - 1);
  } else {
    showToast('已经是第一个推荐视频啦！', 'info');
  }
}

function slideNext() {
  if (activeVideoIndex < recommendedVideos.length - 1) {
    setActiveVideo(activeVideoIndex + 1);
  } else {
    // Reached the end! Fetch new list or show catch up
    showToast('已经为您播完当前的推荐视频，正在拉取最新列表...', 'info');
    fetchRecommendations();
  }
}

function togglePlayPause(index) {
  const player = document.getElementById(`player-${index}`);
  const indicator = document.getElementById(`play-indicator-${index}`);
  
  if (player.paused) {
    player.play();
    indicator.classList.remove('active');
  } else {
    player.pause();
    indicator.classList.add('active');
  }
}

function updateVideoProgress(index) {
  const player = document.getElementById(`player-${index}`);
  const progressBar = document.getElementById(`progress-${index}`);
  const timeLabel = document.getElementById(`time-${index}`);

  if (player && progressBar) {
    const duration = Number.isFinite(player.duration) ? player.duration : 0;
    const percentage = duration > 0 ? (player.currentTime / duration) * 100 : 0;
    progressBar.style.width = `${percentage}%`;
    if (timeLabel) {
      timeLabel.innerText = `${formatVideoTime(player.currentTime)} / ${formatVideoTime(duration)}`;
    }
  }
}

function renderPlaybackSpeedOptions() {
  return PLAYBACK_SPEED_OPTIONS.map(rate => `
    <option value="${rate}" ${rate === selectedPlaybackRate ? 'selected' : ''}>${rate}x</option>
  `).join('');
}

function formatVideoTime(seconds) {
  if (!Number.isFinite(seconds) || seconds < 0) {
    return '00:00';
  }

  const totalSeconds = Math.floor(seconds);
  const minutes = Math.floor(totalSeconds / 60);
  const remainSeconds = totalSeconds % 60;
  return `${String(minutes).padStart(2, '0')}:${String(remainSeconds).padStart(2, '0')}`;
}

function seekVideo(event, index) {
  const player = document.getElementById(`player-${index}`);
  const track = event.currentTarget;
  if (!player || !track || !Number.isFinite(player.duration) || player.duration <= 0) {
    return;
  }

  const rect = track.getBoundingClientRect();
  const ratio = Math.min(Math.max((event.clientX - rect.left) / rect.width, 0), 1);
  player.currentTime = player.duration * ratio;
  updateVideoProgress(index);
}

function changeVideoSpeed(index, value) {
  const parsedRate = Number(value);
  if (!PLAYBACK_SPEED_OPTIONS.includes(parsedRate)) {
    return;
  }

  selectedPlaybackRate = parsedRate;

  const player = document.getElementById(`player-${index}`);
  if (player) {
    player.playbackRate = parsedRate;
  }

  recommendedVideos.forEach((_, videoIndex) => syncPlaybackRateControl(videoIndex));
}

function syncPlaybackRateControl(index) {
  const speedSelect = document.getElementById(`speed-${index}`);
  if (speedSelect) {
    speedSelect.value = String(selectedPlaybackRate);
  }
}

function handleKeyboardNavigation(e) {
  const activeSection = document.querySelector('.content-section.active');
  if (!activeSection || activeSection.id !== 'section-recommend' || recommendedVideos.length === 0) return;

  if (e.key === 'ArrowUp') {
    e.preventDefault();
    slidePrevious();
  } else if (e.key === 'ArrowDown') {
    e.preventDefault();
    slideNext();
  }
}

function handleWheelNavigation(e) {
  // Simple scroll debounce
  const now = Date.now();
  if (now - lastScrollTime < 800) {
    e.preventDefault();
    return;
  }
  
  e.preventDefault(); // Stop default container scrolling
  lastScrollTime = now;

  if (e.deltaY > 0) {
    slideNext();
  } else if (e.deltaY < 0) {
    slidePrevious();
  }
}

function pauseActiveRecommendVideo() {
  if (activeVideoIndex !== -1) {
    const player = document.getElementById(`player-${activeVideoIndex}`);
    if (player) player.pause();
  }
}

function pauseAllVideos() {
  const videos = document.querySelectorAll('video');
  videos.forEach(v => {
    v.pause();
    v.currentTime = 0;
  });
}

// ----------------------------------------------------
// VIDEO LIKE TOGGLE MANAGER
// ----------------------------------------------------

async function toggleLikeVideo(videoId, index) {
  event.stopPropagation(); // Prevent toggling Play/Pause on overlay click
  
  const likeBtn = document.getElementById(`like-btn-${index}`);
  const likeCount = document.getElementById(`like-count-${index}`);

  try {
    const res = await fetch(`${BASE_URL}/api/videos/${videoId}/like`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`
      }
    });

    const data = await res.json();

    if (data.success) {
      if (data.liked) {
        likeBtn.classList.add('liked');
        showToast('已点赞！💖', 'success');
      } else {
        likeBtn.classList.remove('liked');
        showToast('已取消点赞', 'info');
      }
      likeCount.innerText = data.likes_count;
      
      // Update our memory cache
      recommendedVideos[index].is_liked = data.liked;
      recommendedVideos[index].likes_count = data.likes_count;
    } else {
      showToast(data.message || '点赞失败', 'error');
    }
  } catch (err) {
    showToast('无法连接服务器', 'error');
    console.error(err);
  }
}

async function resetWatchHistory() {
  try {
    const res = await fetch(BASE_URL + '/api/videos/reset-views', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`
      }
    });
    const data = await res.json();
    if (data.success) {
      showToast(data.message, 'success');
      fetchRecommendations();
    }
  } catch (err) {
    showToast('重置观看历史失败', 'error');
    console.error(err);
  }
}

// ----------------------------------------------------
// MY VIDEOS MANAGEMENT & PAGINATION
// ----------------------------------------------------

async function fetchMyVideos(page) {
  myVideosPage = page;
  
  const grid = document.getElementById('my-videos-grid');
  grid.innerHTML = `
    <div class="no-videos-state">
      <div class="spinner"></div>
      <p>正在获取您的个人视频库...</p>
    </div>
  `;

  try {
    const res = await fetch(`${BASE_URL}/api/videos/my?page=${page}&limit=${myVideosLimit}`, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`
      }
    });

    const data = await res.json();

    if (data.success) {
      renderMyVideosGrid(data.videos, data.pagination);
    } else {
      showToast(data.message || '拉取视频列表失败', 'error');
    }
  } catch (err) {
    showToast('网络请求失败', 'error');
    console.error(err);
  }
}

function renderMyVideosGrid(videos, pagination) {
  const grid = document.getElementById('my-videos-grid');
  const pagInfo = document.getElementById('pagination-info');
  const prevBtn = document.getElementById('btn-prev-page');
  const nextBtn = document.getElementById('btn-next-page');
  const pagContainer = document.getElementById('my-videos-pagination');

  // Update Stats count
  document.getElementById('my-video-count').innerText = pagination.total;

  if (videos.length === 0) {
    grid.innerHTML = `
      <div class="no-videos-state">
        <i class="fa-solid fa-video-slash"></i>
        <h3>您尚未发布任何短视频</h3>
        <p>快点击下方或顶部的“发布视频”按钮，分享你的精彩生活！</p>
        <button class="btn btn-outline" onclick="openUploadModal()">立即上传</button>
      </div>
    `;
    pagContainer.classList.add('hidden');
    return;
  }

  grid.innerHTML = '';
  
  videos.forEach(video => {
    const card = document.createElement('div');
    card.className = 'personal-video-card';
    card.innerHTML = `
      <!-- Thumbnail Cover Block -->
      <div class="card-thumbnail-wrapper" onclick="openPreviewModal('${video.video_url}')">
        <img src="${video.cover_url}" alt="${video.title}" onerror="this.src='https://placehold.co/600x800/121216/ffffff?text=Video+Cover'">
        <div class="card-play-overlay">
          <i class="fa-solid fa-circle-play"></i>
        </div>
        <div class="card-likes-badge">
          <i class="fa-solid fa-heart"></i> <span>${video.likes_count}</span>
        </div>
      </div>

      <!-- Card details Info -->
      <div class="card-body">
        <h4>${video.title}</h4>
        <p>${video.description || '无详细描述'}</p>
        <div class="card-footer">
          <span class="card-date"><i class="fa-regular fa-calendar"></i> ${formatDate(video.created_at)}</span>
          <button class="card-btn-delete" onclick="handleDeleteVideo(event, ${video.id})" title="删除此视频 (权限控制)">
            <i class="fa-solid fa-trash-can"></i>
          </button>
        </div>
      </div>
    `;
    grid.appendChild(card);
  });

  // Manage pagination state
  pagContainer.classList.remove('hidden');
  pagInfo.innerText = `第 ${pagination.page} / ${pagination.totalPages || 1} 页 (共 ${pagination.total} 个视频)`;
  
  prevBtn.disabled = pagination.page <= 1;
  nextBtn.disabled = pagination.page >= pagination.totalPages;
}

function changeMyVideosPage(dir) {
  fetchMyVideos(myVideosPage + dir);
}

function handleDeleteVideo(event, videoId) {
  event.stopPropagation();
  if (confirm('🔒 安全校验：您确定要永久删除这个视频作品吗？此操作将立即从云端库销毁，且不可恢复！')) {
    executeDeleteVideo(videoId);
  }
}

async function executeDeleteVideo(videoId) {
  try {
    const res = await fetch(`${BASE_URL}/api/videos/${videoId}`, {
      method: 'DELETE',
      headers: {
        'Authorization': `Bearer ${token}`
      }
    });

    const data = await res.json();

    if (data.success) {
      showToast(data.message, 'success');
      // Re-fetch current page list
      fetchMyVideos(myVideosPage);
    } else {
      showToast(data.message || '删除失败，请验证您的操作权限！', 'error');
    }
  } catch (err) {
    showToast('删除请求发生异常', 'error');
    console.error(err);
  }
}

// Fullscreen Popout Video Player for作品列表
function openPreviewModal(url) {
  const modal = document.getElementById('preview-modal');
  const player = document.getElementById('preview-video-player');
  
  player.src = url;
  modal.classList.add('active');
  player.play();
}

function closePreviewModal(event) {
  const modal = document.getElementById('preview-modal');
  const player = document.getElementById('preview-video-player');
  
  player.pause();
  player.src = '';
  modal.classList.remove('active');
}

// ----------------------------------------------------
// PUBLISH VIDEO SYSTEM (Multer uploader + Progress)
// ----------------------------------------------------

function openUploadModal() {
  if (!token) {
    showToast('请先登录您的创作者账户！', 'warning');
    showAuthOverlay(true);
    return;
  }
  document.getElementById('upload-modal').classList.add('active');
}

function closeUploadModal() {
  document.getElementById('upload-modal').classList.remove('active');
  resetUploadForm();
}

function resetUploadForm() {
  document.getElementById('upload-video-form').reset();
  
  // Reset dropzones
  const vDrop = document.getElementById('video-dropzone');
  vDrop.innerHTML = `
    <i class="fa-solid fa-film dropzone-icon"></i>
    <p class="dropzone-text">点击选择 或 拖拽 MP4 格式视频文件到这里</p>
    <p class="dropzone-sub">支持 .mp4, .webm 等格式 (最大支持 100MB)</p>
    <span id="selected-video-filename" class="selected-filename hidden"></span>
  `;
  
  const cDrop = document.getElementById('cover-dropzone');
  cDrop.innerHTML = `
    <i class="fa-solid fa-image dropzone-icon-sm"></i>
    <p class="dropzone-text">点击上传封面图片文件 (不选则由系统自动生成彩色渐变封面)</p>
    <span id="selected-cover-filename" class="selected-filename hidden"></span>
  `;

  document.getElementById('upload-progress-container').classList.add('hidden');
  document.getElementById('upload-progress-bar').style.width = '0%';
  document.getElementById('upload-percentage').innerText = '0%';
  document.getElementById('btn-publish-submit').disabled = false;
}

function handleFileSelected(event, type) {
  const file = event.target.files[0];
  if (!file) return;

  const dropzoneId = type === 'video' ? 'video-dropzone' : 'cover-dropzone';
  const dropzone = document.getElementById(dropzoneId);
  const icon = type === 'video' ? 'fa-solid fa-circle-check text-success' : 'fa-solid fa-image-polaroid text-success';

  dropzone.innerHTML = `
    <i class="${icon}" style="font-size:32px; color:var(--success);"></i>
    <p class="dropzone-text" style="color:var(--success)">已选中 ${type === 'video' ? '视频文件' : '封面图像'}</p>
    <span class="selected-filename">${file.name} (${formatBytes(file.size)})</span>
  `;
}

async function handlePublishVideo(event) {
  event.preventDefault();

  const title = document.getElementById('upload-title').value.trim();
  const description = document.getElementById('upload-desc').value.trim();
  const videoFile = document.getElementById('upload-video-file').files[0];
  const coverFile = document.getElementById('upload-cover-file').files[0];

  if (!title) {
    showToast('视频标题是必填项！', 'warning');
    return;
  }
  if (!videoFile) {
    showToast('请先选择需要上传的视频文件！', 'warning');
    return;
  }

  // Construct Form Data
  const formData = new FormData();
  formData.append('title', title);
  formData.append('description', description);
  formData.append('video', videoFile);
  if (coverFile) {
    formData.append('cover', coverFile);
  }

  // Visual Setup for uploading
  const progressContainer = document.getElementById('upload-progress-container');
  const progressBar = document.getElementById('upload-progress-bar');
  const percentageText = document.getElementById('upload-percentage');
  const statusText = document.getElementById('upload-status-text');
  const submitBtn = document.getElementById('btn-publish-submit');

  progressContainer.classList.remove('hidden');
  submitBtn.disabled = true;

  // Use XMLHttpRequest to monitor exact upload percentage
  const xhr = new XMLHttpRequest();
  xhr.open('POST', BASE_URL + '/api/videos/publish');
  xhr.setRequestHeader('Authorization', `Bearer ${token}`);

  // Track upload progress
  xhr.upload.addEventListener('progress', (e) => {
    if (e.lengthComputable) {
      const percentComplete = Math.round((e.loaded / e.total) * 100);
      progressBar.style.width = `${percentComplete}%`;
      percentageText.innerText = `${percentComplete}%`;
      
      if (percentComplete === 100) {
        statusText.innerText = '云端处理并配置推荐索引中...';
      } else {
        statusText.innerText = '正在上传数据包至服务器...';
      }
    }
  });

  // Handle Response completion
  xhr.addEventListener('load', () => {
    let response = {};
    try {
      response = JSON.parse(xhr.responseText);
    } catch (e) {
      response = { success: false, message: '解析服务器返回出错' };
    }

    if (xhr.status === 200 || xhr.status === 201) {
      if (response.success) {
        showToast('恭喜，视频发布成功，已进入推荐系统库！🎉', 'success');
        closeUploadModal();
        
        // Refresh My Videos if on page
        const activeSection = document.querySelector('.content-section.active');
        if (activeSection.id === 'section-my-videos') {
          fetchMyVideos(1);
        } else {
          switchSection('my-videos');
        }
      } else {
        showToast(response.message || '上传发布视频失败！', 'error');
        submitBtn.disabled = false;
      }
    } else {
      showToast(response.message || '请求处理失败！', 'error');
      submitBtn.disabled = false;
    }
  });

  xhr.addEventListener('error', () => {
    showToast('上传出错，请检查网络或文件大小！', 'error');
    submitBtn.disabled = false;
  });

  xhr.send(formData);
}

// ----------------------------------------------------
// MONITORING & DIAGNOSTIC SYSTEM CONTROLLER
// ----------------------------------------------------

async function fetchSystemStatsAndLogs() {
  if (!token) return;

  try {
    // 1. Fetch statistics
    const statsRes = await fetch(BASE_URL + '/api/admin/stats', {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`
      }
    });
    const statsData = await statsRes.json();

    if (statsData.success) {
      document.getElementById('metric-users').innerText = statsData.stats.users;
      document.getElementById('metric-videos').innerText = statsData.stats.videos;
      document.getElementById('metric-likes').innerText = statsData.stats.likes;
      
      const latencyVal = document.getElementById('metric-latency');
      latencyVal.innerText = `${statsData.stats.averageResponseTimeMs.toFixed(2)} ms`;
      
      // Highlight color depending on performance
      if (statsData.stats.averageResponseTimeMs > 50) {
        latencyVal.style.color = 'var(--warning)';
      } else {
        latencyVal.style.color = 'var(--success)';
      }
    }

    // 2. Fetch logs
    const logsRes = await fetch(BASE_URL + '/api/admin/logs', {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`
      }
    });
    const logsData = await logsRes.json();

    if (logsData.success) {
      renderLogsTable(logsData.logs);
    }
  } catch (err) {
    console.error('Failed to query diagnostics info:', err);
  }
}

function renderLogsTable(logs) {
  const tbody = document.getElementById('logs-table-body');
  if (logs.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="7" class="text-center text-muted">暂无请求日志数据</td>
      </tr>
    `;
    return;
  }

  tbody.innerHTML = '';
  
  logs.forEach((log, index) => {
    // Format timestamp
    const logTime = new Date(log.timestamp).toLocaleTimeString();
    
    // Format HTTP method badge
    const methodLower = log.method.toLowerCase();
    const methodBadge = `<span class="badge-method ${methodLower}">${log.method}</span>`;
    
    // Format response duration (Slow warning threshold > 10ms for local db)
    const durationClass = log.durationMs > 15 ? 'slow' : 'fast';
    
    // Status Badge
    let statusClass = 's2xx';
    if (log.outputs.statusCode >= 400 && log.outputs.statusCode < 500) statusClass = 's4xx';
    else if (log.outputs.statusCode >= 500) statusClass = 's5xx';
    
    const statusBadge = `<span class="badge-status ${statusClass}">${log.outputs.statusCode}</span>`;

    // Operating user representation
    const userDisplay = typeof log.user === 'object' 
      ? `@${log.user.username}` 
      : `<span class="text-muted">${log.user}</span>`;

    // Row construction
    const row = document.createElement('tr');
    row.innerHTML = `
      <td class="log-time">${logTime}</td>
      <td>${methodBadge}</td>
      <td class="log-path">${log.url}</td>
      <td class="log-user">${userDisplay}</td>
      <td class="log-duration ${durationClass}">${log.durationMs.toFixed(2)} ms</td>
      <td>${statusBadge}</td>
      <td>
        <button class="log-payload-btn" onclick="toggleLogPayload(${index})">
          <i class="fa-solid fa-code"></i> 展开负载详情
        </button>
      </td>
    `;
    tbody.appendChild(row);

    // Collapsible Detail Row
    const detailRow = document.createElement('tr');
    detailRow.id = `log-detail-row-${index}`;
    detailRow.className = 'log-payload-details-row';
    detailRow.style.display = 'none';
    
    // Sanitize circular bodies if needed
    const inputsJson = JSON.stringify(log.inputs, null, 2);
    const outputsJson = JSON.stringify(log.outputs, null, 2);

    detailRow.innerHTML = `
      <td colspan="7">
        <div class="log-details-card">
          <div class="payload-block">
            <h5>📥 REQUEST INPUTS (Query & Body)</h5>
            <pre>${escapeHtml(inputsJson)}</pre>
          </div>
          <div class="payload-block output">
            <h5>📤 RESPONSE OUTPUTS (JSON Data)</h5>
            <pre>${escapeHtml(outputsJson)}</pre>
          </div>
        </div>
      </td>
    `;
    tbody.appendChild(detailRow);
  });
}

function toggleLogPayload(index) {
  const detailRow = document.getElementById(`log-detail-row-${index}`);
  if (detailRow.style.display === 'none') {
    detailRow.style.display = 'table-row';
  } else {
    detailRow.style.display = 'none';
  }
}

// ----------------------------------------------------
// COMPACT UTILITIES & FORMATTERS
// ----------------------------------------------------

function showToast(message, type = 'success') {
  const toast = document.getElementById('toast-notification');
  const icon = document.getElementById('toast-icon');
  const msgText = document.getElementById('toast-message');

  // Reset classes
  toast.className = 'toast';
  toast.classList.add(type);
  
  // Set Icon matching types
  let iconClass = 'fa-circle-check';
  if (type === 'error') iconClass = 'fa-triangle-exclamation';
  else if (type === 'info') iconClass = 'fa-circle-info';
  else if (type === 'warning') iconClass = 'fa-circle-exclamation';

  icon.className = `fa-solid ${iconClass}`;
  msgText.innerText = message;

  // Show Toast with animation
  toast.classList.remove('hidden');

  // Automatically hide after 3 seconds
  setTimeout(() => {
    toast.classList.add('hidden');
  }, 3000);
}

function formatDate(dateStr) {
  const date = new Date(dateStr);
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  const hr = String(date.getHours()).padStart(2, '0');
  const min = String(date.getMinutes()).padStart(2, '0');
  return `${y}-${m}-${d} ${hr}:${min}`;
}

function formatBytes(bytes, decimals = 2) {
  if (bytes === 0) return '0 Bytes';
  const k = 1024;
  const dm = decimals < 0 ? 0 : decimals;
  const sizes = ['Bytes', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
}

function escapeHtml(text) {
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}
