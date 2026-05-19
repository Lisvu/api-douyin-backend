// UI text polish and small behavior fixes layered on top of the original app logic.

async function handleAuth(event, type) {
  event.preventDefault();

  const usernameInput = document.getElementById(`${type}-username`).value.trim();
  const passwordInput = document.getElementById(`${type}-password`).value.trim();

  if (!usernameInput || !passwordInput) {
    showToast('请输入完整的用户名和密码', 'warning');
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
      showToast(data.message || '登录成功', 'success');
      showAuthOverlay(false);
      document.getElementById(`${type}-username`).value = '';
      document.getElementById(`${type}-password`).value = '';
      initializeSession();
    } else {
      showToast(data.message || '操作失败', 'error');
    }
  } catch (err) {
    showToast('无法连接服务器，请检查后端服务', 'error');
    console.error(err);
  }
}

function handleLogout() {
  token = null;
  currentUser = null;
  localStorage.removeItem('douyin_token');
  localStorage.removeItem('douyin_user');
  pauseAllVideos();
  document.getElementById('display-username').innerText = '未登录';

  if (monitorIntervalId) {
    clearInterval(monitorIntervalId);
    monitorIntervalId = null;
  }

  showAuthOverlay(true);
  switchAuthTab('login');
  showToast('已安全退出登录', 'info');
}

function confirmAccountDeletion() {
  if (confirm('确认注销当前账号吗？此操作不可恢复，并会删除你发布的视频。')) {
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
      showToast(data.message || '账号已注销', 'success');
      token = null;
      currentUser = null;
      localStorage.removeItem('douyin_token');
      localStorage.removeItem('douyin_user');
      showAuthOverlay(true);
    } else {
      showToast(data.message || '注销账号失败', 'error');
    }
  } catch (err) {
    showToast('无法连接服务器', 'error');
    console.error(err);
  }
}

function switchSection(sectionId) {
  document.querySelectorAll('.content-section').forEach(section => section.classList.remove('active'));
  document.querySelectorAll('.nav-item').forEach(item => item.classList.remove('active'));

  document.getElementById(`section-${sectionId}`).classList.add('active');
  document.getElementById(`nav-${sectionId}`).classList.add('active');

  if (sectionId !== 'recommend') {
    pauseActiveRecommendVideo();
  }

  if (sectionId !== 'monitor' && monitorIntervalId) {
    clearInterval(monitorIntervalId);
    monitorIntervalId = null;
  }

  const title = document.getElementById('page-title');
  const desc = document.getElementById('page-desc');

  if (sectionId === 'recommend') {
    title.innerHTML = '<i class="fa-solid fa-compass"></i> 视频推荐流';
    desc.innerText = '按观看历史与点赞热度生成推荐列表，支持滚轮和方向键切换。';
    fetchRecommendations();
  } else if (sectionId === 'my-videos') {
    title.innerHTML = '<i class="fa-solid fa-video"></i> 我的视频作品';
    desc.innerText = '管理你发布的视频内容、封面、点赞数据与删除操作。';
    myVideosPage = 1;
    fetchMyVideos(myVideosPage);
  } else if (sectionId === 'monitor') {
    title.innerHTML = '<i class="fa-solid fa-chart-line"></i> 系统实时监控';
    desc.innerText = '查看接口耗时、请求日志和系统核心指标。';
    fetchSystemStatsAndLogs();

    if (!monitorIntervalId) {
      monitorIntervalId = setInterval(fetchSystemStatsAndLogs, 3000);
    }
  }
}

async function fetchRecommendations() {
  const container = document.getElementById('feed-player-container');
  container.innerHTML = `
    <div class="empty-state-loader">
      <div class="spinner"></div>
      <p>正在加载推荐视频...</p>
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
      showToast(data.message || '推荐视频加载失败', 'error');
    }
  } catch (err) {
    showToast('加载失败，请确认后端服务已启动', 'error');
    console.error(err);
  }
}

function renderRecommendCarousel() {
  const container = document.getElementById('feed-player-container');
  container.innerHTML = '';

  if (recommendedVideos.length === 0) {
    container.innerHTML = `
      <div class="no-more-videos">
        <i class="fa-solid fa-circle-check"></i>
        <h3>已浏览完当前推荐</h3>
        <p>你看过的视频不会重复推荐。可以重置观看记录后重新体验推荐流程。</p>
        <button class="btn btn-primary" onclick="resetWatchHistory()">
          <i class="fa-solid fa-rotate-left"></i>
          重置观看记录
        </button>
      </div>
    `;
    return;
  }

  recommendedVideos.forEach((video, index) => {
    const slide = document.createElement('div');
    slide.className = `video-slide ${index === 0 ? 'active' : 'next'}`;
    slide.id = `slide-video-${index}`;

    const coverImageHtml = video.cover_url
      ? `<img src="${video.cover_url}" style="position:absolute;width:100%;height:100%;object-fit:cover;filter:blur(12px);opacity:0.25;z-index:0;" alt="">`
      : '';

    slide.innerHTML = `
      ${coverImageHtml}
      <video id="player-${index}"
             src="${video.video_url}"
             loop
             preload="auto"
             playsinline
             onclick="togglePlayPause(${index})"
             ontimeupdate="updateVideoProgress(${index})">
      </video>
      <div class="video-overlay-gradient-top"></div>
      <div class="video-overlay-gradient-bottom"></div>
      <div id="play-indicator-${index}" class="video-play-indicator">
        <i class="fa-solid fa-play"></i>
      </div>
      <div id="progress-${index}" class="video-progress-line"></div>
      <div class="video-details">
        <div class="creator-name">${video.creator_name || '创作者'}</div>
        <div class="video-title">${video.title}</div>
        <div class="video-desc">${video.description || ''}</div>
      </div>
      <div class="video-actions-sidebar">
        <div class="action-creator-avatar">
          <i class="fa-solid fa-user"></i>
        </div>
        <div class="action-btn-wrapper">
          <button id="like-btn-${index}"
                  class="action-circle-btn ${video.is_liked ? 'liked' : ''}"
                  onclick="toggleLikeVideo(${video.id}, ${index})">
            <i class="fa-solid fa-heart"></i>
          </button>
          <span id="like-count-${index}" class="action-label">${video.likes_count}</span>
        </div>
        <div class="action-btn-wrapper">
          <button class="action-circle-btn" onclick="showToast('分享链接已复制', 'success')">
            <i class="fa-solid fa-share"></i>
          </button>
          <span class="action-label">分享</span>
        </div>
      </div>
    `;

    container.appendChild(slide);
  });

  setActiveVideo(0);
}

async function fetchMyVideos(page) {
  myVideosPage = page;

  const grid = document.getElementById('my-videos-grid');
  grid.innerHTML = `
    <div class="no-videos-state">
      <div class="spinner"></div>
      <p>正在获取你的视频列表...</p>
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
      showToast(data.message || '视频列表加载失败', 'error');
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

  document.getElementById('my-video-count').innerText = pagination.total;

  if (videos.length === 0) {
    grid.innerHTML = `
      <div class="no-videos-state">
        <i class="fa-solid fa-video-slash"></i>
        <h3>还没有发布视频</h3>
        <p>点击发布按钮上传第一个作品，测试推荐、观看和点赞流程。</p>
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
      <div class="card-thumbnail-wrapper" onclick="openPreviewModal('${video.video_url}')">
        <img src="${video.cover_url}" alt="${video.title}" onerror="this.src='https://placehold.co/600x800/15181d/f7f3ee?text=Video+Cover'">
        <div class="card-play-overlay">
          <i class="fa-solid fa-circle-play"></i>
        </div>
        <div class="card-likes-badge">
          <i class="fa-solid fa-heart"></i>
          <span>${video.likes_count}</span>
        </div>
      </div>
      <div class="card-body">
        <h4>${video.title}</h4>
        <p>${video.description || '暂无描述'}</p>
        <div class="card-footer">
          <span class="card-date"><i class="fa-regular fa-calendar"></i> ${formatDate(video.created_at)}</span>
          <button class="card-btn-delete" onclick="handleDeleteVideo(event, ${video.id})" title="删除视频">
            <i class="fa-solid fa-trash-can"></i>
          </button>
        </div>
      </div>
    `;
    grid.appendChild(card);
  });

  pagContainer.classList.remove('hidden');
  pagInfo.innerText = `第 ${pagination.page} / ${pagination.totalPages || 1} 页，共 ${pagination.total} 个视频`;
  prevBtn.disabled = pagination.page <= 1;
  nextBtn.disabled = pagination.page >= pagination.totalPages;
}

function handleDeleteVideo(event, videoId) {
  event.stopPropagation();
  if (confirm('确认永久删除这个视频吗？此操作不可恢复。')) {
    executeDeleteVideo(videoId);
  }
}

async function handlePublishVideo(event) {
  event.preventDefault();

  const title = document.getElementById('upload-title').value.trim();
  const description = document.getElementById('upload-desc').value.trim();
  const videoFile = document.getElementById('upload-video-file').files[0];
  const coverFile = document.getElementById('upload-cover-file').files[0];

  if (!title) {
    showToast('视频标题是必填项', 'warning');
    return;
  }

  if (!videoFile) {
    showToast('请先选择需要上传的视频文件', 'warning');
    return;
  }

  const formData = new FormData();
  formData.append('title', title);
  formData.append('description', description);
  formData.append('video', videoFile);
  if (coverFile) {
    formData.append('cover', coverFile);
  }

  const progressContainer = document.getElementById('upload-progress-container');
  const progressBar = document.getElementById('upload-progress-bar');
  const percentageText = document.getElementById('upload-percentage');
  const statusText = document.getElementById('upload-status-text');
  const submitBtn = document.getElementById('btn-publish-submit');

  progressContainer.classList.remove('hidden');
  submitBtn.disabled = true;

  const xhr = new XMLHttpRequest();
  xhr.open('POST', BASE_URL + '/api/videos/publish');
  xhr.setRequestHeader('Authorization', `Bearer ${token}`);

  xhr.upload.addEventListener('progress', (e) => {
    if (!e.lengthComputable) return;

    const percentComplete = Math.round((e.loaded / e.total) * 100);
    progressBar.style.width = `${percentComplete}%`;
    percentageText.innerText = `${percentComplete}%`;
    statusText.innerText = percentComplete === 100
      ? '服务器正在处理视频...'
      : '正在上传视频文件...';
  });

  xhr.addEventListener('load', () => {
    let response = {};
    try {
      response = JSON.parse(xhr.responseText);
    } catch (e) {
      response = { success: false, message: '服务器返回解析失败' };
    }

    if ((xhr.status === 200 || xhr.status === 201) && response.success) {
      showToast(response.message || '视频发布成功', 'success');
      closeUploadModal();

      const activeSection = document.querySelector('.content-section.active');
      if (activeSection.id === 'section-my-videos') {
        fetchMyVideos(1);
      } else {
        switchSection('my-videos');
      }
    } else {
      showToast(response.message || '视频发布失败', 'error');
      submitBtn.disabled = false;
    }
  });

  xhr.addEventListener('error', () => {
    showToast('上传出错，请检查网络或文件大小', 'error');
    submitBtn.disabled = false;
  });

  xhr.send(formData);
}

function resetUploadForm() {
  document.getElementById('upload-video-form').reset();

  const vDrop = document.getElementById('video-dropzone');
  vDrop.innerHTML = `
    <i class="fa-solid fa-film dropzone-icon"></i>
    <p class="dropzone-text">点击选择或拖拽 MP4 / WebM 视频文件</p>
    <p class="dropzone-sub">最大支持 100MB</p>
    <span id="selected-video-filename" class="selected-filename hidden"></span>
    <input type="file" id="upload-video-file" accept="video/mp4,video/webm" style="display:none" onchange="handleFileSelected(event, 'video')">
  `;

  const cDrop = document.getElementById('cover-dropzone');
  cDrop.innerHTML = `
    <i class="fa-solid fa-image dropzone-icon-sm"></i>
    <p class="dropzone-text">点击上传封面图，不选择则使用系统默认封面</p>
    <span id="selected-cover-filename" class="selected-filename hidden"></span>
    <input type="file" id="upload-cover-file" accept="image/*" style="display:none" onchange="handleFileSelected(event, 'cover')">
  `;

  document.getElementById('upload-progress-container').classList.add('hidden');
  document.getElementById('upload-progress-bar').style.width = '0%';
  document.getElementById('upload-percentage').innerText = '0%';
  document.getElementById('upload-status-text').innerText = '正在上传视频资源...';
  document.getElementById('btn-publish-submit').disabled = false;
}

function handleFileSelected(event, type) {
  const file = event.target.files[0];
  if (!file) return;

  const input = event.target;
  const dropzone = document.getElementById(type === 'video' ? 'video-dropzone' : 'cover-dropzone');
  const label = type === 'video' ? '视频文件' : '封面图像';

  dropzone.innerHTML = `
    <i class="fa-solid fa-circle-check" style="font-size:32px;color:var(--success);"></i>
    <p class="dropzone-text" style="color:var(--success)">已选择${label}</p>
    <span class="selected-filename">${file.name} (${formatBytes(file.size)})</span>
  `;

  input.style.display = 'none';
  dropzone.appendChild(input);
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
    const logTime = new Date(log.timestamp).toLocaleTimeString();
    const methodLower = log.method.toLowerCase();
    const methodBadge = `<span class="badge-method ${methodLower}">${log.method}</span>`;
    const durationClass = log.durationMs > 15 ? 'slow' : 'fast';

    let statusClass = 's2xx';
    if (log.outputs.statusCode >= 400 && log.outputs.statusCode < 500) statusClass = 's4xx';
    else if (log.outputs.statusCode >= 500) statusClass = 's5xx';

    const statusBadge = `<span class="badge-status ${statusClass}">${log.outputs.statusCode}</span>`;
    const userDisplay = typeof log.user === 'object'
      ? `@${log.user.username}`
      : `<span class="text-muted">${log.user}</span>`;

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
          <i class="fa-solid fa-code"></i>
          查看详情
        </button>
      </td>
    `;
    tbody.appendChild(row);

    const detailRow = document.createElement('tr');
    detailRow.id = `log-detail-row-${index}`;
    detailRow.className = 'log-payload-details-row';
    detailRow.style.display = 'none';
    detailRow.innerHTML = `
      <td colspan="7">
        <div class="log-details-card">
          <div class="payload-block">
            <h5>REQUEST INPUTS</h5>
            <pre>${escapeHtml(JSON.stringify(log.inputs, null, 2))}</pre>
          </div>
          <div class="payload-block output">
            <h5>RESPONSE OUTPUTS</h5>
            <pre>${escapeHtml(JSON.stringify(log.outputs, null, 2))}</pre>
          </div>
        </div>
      </td>
    `;
    tbody.appendChild(detailRow);
  });
}
