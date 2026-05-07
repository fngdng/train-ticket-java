const form = document.getElementById('searchForm');
const tripsEl = document.getElementById('trips');
const newsList = document.getElementById('newsList');
const sampleBtn = document.getElementById('sampleBtn');

const mockTrips = [
  {name:'SE1',from:'Hanoi',to:'Ho Chi Minh',departure:'06:00',arrival:'18:45',duration:'12h45p',price:980000,availableSeats:120},
  {name:'SE3',from:'Hanoi',to:'Da Nang',departure:'08:30',arrival:'18:10',duration:'9h40p',price:680000,availableSeats:88},
  {name:'SE5',from:'Ho Chi Minh',to:'Da Nang',departure:'07:15',arrival:'19:05',duration:'11h50p',price:720000,availableSeats:94},
  {name:'SE7',from:'Hai Phong',to:'Hanoi',departure:'09:00',arrival:'11:10',duration:'2h10p',price:180000,availableSeats:200},
  {name:'SE9',from:'Da Nang',to:'Ho Chi Minh',departure:'14:20',arrival:'02:15',duration:'11h55p',price:760000,availableSeats:76}
];

const mockNews = [
  {title:'Cập nhật biểu giá vé mới',link:'#'},
  {title:'Ưu đãi 25% phí di chuyển',link:'#'},
  {title:'Tàu Hà Nội - Nha Trang chạy lại',link:'#'}
];

function readJsonStorage(key){
  try{
    return JSON.parse(localStorage.getItem(key) || '{}');
  }catch(err){
    return {};
  }
}

function logoutAll(){
  localStorage.removeItem('auth_token');
  localStorage.removeItem('auth_user');
  localStorage.removeItem('admin_token');
  localStorage.removeItem('admin_user');
  sessionStorage.removeItem('pending_train');
  sessionStorage.removeItem('selected_train');
  window.location.href = '/';
}

function renderAuthArea(){
  const authArea = document.getElementById('authArea');
  if(!authArea){
    return;
  }

  const authToken = localStorage.getItem('auth_token');
  const authUser = readJsonStorage('auth_user');
  const adminToken = localStorage.getItem('admin_token');
  const adminUser = readJsonStorage('admin_user');

  const isAdmin = !!adminToken && (adminUser.role || '').toUpperCase() === 'ADMIN';
  const isUser = !!authToken;

  if(isAdmin){
    const adminName = adminUser.fullName || adminUser.username || 'Admin';
    authArea.innerHTML = `
      <span class="auth-pill">Xin chào, ${adminName}</span>
      <a class="btn-link" href="/tickets.html">Vé của tôi</a>
      <a class="btn-link" href="/admin-dashboard.html">Vào dashboard</a>
      <a class="btn-link" href="#" id="logoutLink">Đăng xuất</a>
    `;
  }else if(isUser){
    const userName = authUser.fullName || authUser.username || 'Khách';
    authArea.innerHTML = `
      <span class="auth-pill">Xin chào, ${userName}</span>
      <a class="btn-link" href="/account.html">Tài khoản</a>
      <a class="btn-link" href="/tickets.html">Vé của tôi</a>
      <a class="btn-link" href="/checkout.html">Giỏ đặt vé</a>
      <a class="btn-link" href="#" id="logoutLink">Đăng xuất</a>
    `;
  }else{
    authArea.innerHTML = '<a class="btn-link" href="/auth.html">Đăng nhập / Đăng ký</a>';
  }

  const logoutLink = document.getElementById('logoutLink');
  if(logoutLink){
    logoutLink.addEventListener('click', event => {
      event.preventDefault();
      logoutAll();
    });
  }
}

function renderTrips(trips){
  tripsEl.innerHTML = '';
  if(!trips || trips.length===0){tripsEl.innerHTML = '<p>Không có chuyến nào.</p>';return}
  trips.forEach(t=>{
    const el = document.createElement('div');el.className='trip-card';
    const price = t.price||t.fare||t.cost||0;
    const departure = t.departure||t.dep||t.depTime||'';
    const arrival = t.arrival||t.arr||t.arrTime||'';
    const duration = t.duration||t.durationText||'';
    const seats = t.availableSeats||t.seats||t.available||0;

    el.innerHTML = `<div class="trip-title">${t.name||t.code||'--'} — ${t.from||t.origin||''} → ${t.to||t.destination||''}</div>
      <div class="trip-meta">Khởi hành: ${departure} • Kết thúc: ${arrival} • Thời gian: ${duration}</div>
      <div class="trip-meta">Ghế trống: ${seats}</div>
      <div class="price">${Number(price).toLocaleString()} VND</div>
      <div style="margin-top:10px"><button class="btn primary" data-train='${JSON.stringify(t).replace(/'/g,"\'")}' onclick="choose(this)">Chọn</button></div>`;
    tripsEl.appendChild(el);
  })
}

function renderNews(){
  newsList.innerHTML='';
  mockNews.forEach(n=>{
    const li=document.createElement('li');li.innerHTML=`<a href="${n.link}">${n.title}</a>`;newsList.appendChild(li);
  })
}

form.addEventListener('submit',async e=>{
  e.preventDefault();
  const from = document.getElementById('from').value;
  const to = document.getElementById('to').value;
  const date = document.getElementById('date').value;

  // Try calling local /trains/search if available (proxy), otherwise use mock
  try{
    const q = new URLSearchParams({from,to,date}).toString();
    const res = await fetch(`/trains/search?${q}`,{cache:'no-store'});
    if(res.ok){
      const data = await res.json();
      // normalize array items if needed
      const normalized = (data||[]).map(item=>({
        name: item.name||item.code||item.train,
        from: item.from||item.origin||item.start,
        to: item.to||item.destination||item.end,
        departure: item.departure||item.dep||item.depTime,
        arrival: item.arrival||item.arr||item.arrTime,
        duration: item.duration||item.durationText,
        price: item.price||item.fare||item.cost,
        availableSeats: item.availableSeats||item.seats||item.available
      }));
      renderTrips(normalized);
      return;
    }
  }catch(err){console.debug('remote search failed',err)}

  // fallback: filter mock
  const filtered = mockTrips.filter(t=>t.from.toLowerCase().includes(from.toLowerCase())||from==='')
    .filter(t=>t.to.toLowerCase().includes(to.toLowerCase())||to==='');
  renderTrips(filtered);
});

sampleBtn.addEventListener('click',()=>renderTrips(mockTrips));

async function loadInitialTrips(){
  try{
    const res = await fetch('/trains',{cache:'no-store'});
    if(res.ok){
      const data = await res.json();
      const normalized = (data || []).filter(item => item.active !== false).map(item => ({
        name: item.name || item.code || item.train,
        from: item.from || item.origin || item.start,
        to: item.to || item.destination || item.end,
        departure: item.departure || item.dep || item.depTime,
        arrival: item.arrival || item.arr || item.arrTime,
        duration: item.duration || item.durationText || '',
        price: item.price || item.fare || item.cost,
        availableSeats: item.availableSeats || item.seats || item.available
      }));
      if(normalized.length > 0){
        renderTrips(normalized);
        return;
      }
    }
  }catch(err){
    console.debug('initial trains load failed', err);
  }
  renderTrips(mockTrips);
}

// init
renderNews();
loadInitialTrips();
renderAuthArea();

// Booking action: require login, then send user to checkout form
async function choose(btn){
  try{
    const payload = JSON.parse(btn.getAttribute('data-train'));
    const token = localStorage.getItem('auth_token');
    if(!token){
      sessionStorage.setItem('pending_train', JSON.stringify(payload));
      window.location.href = '/auth.html';
      return;
    }
    sessionStorage.setItem('selected_train', JSON.stringify(payload));
    window.location.href = '/checkout.html';
  }catch(err){console.error(err);alert('Lỗi tạo booking')}
}
