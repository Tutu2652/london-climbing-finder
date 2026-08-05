const DATA = window.CLIMBING_DATA;
/* ----- air conditioning (data lives in gyms.json: per-gym `ac` and `ac_note`) ----- */
// ac: 'full' | 'partial' | absent (no confirmed AC).
const acLevel = g => g.ac || 'none';
const acDesc  = g => g.ac_note || '';
// filter value: '' = all gyms, 'ac' = any AC (full or partial), 'full' = full AC only.
function acMatch(g, ac){
  if(!ac) return true;
  if(ac === 'full') return acLevel(g) === 'full';
  return acLevel(g) !== 'none';
}
const WD = ["Mon","Tue","Wed","Thu","Fri","Sat","Sun"];

/* ----- price logic (mirrors london_climbing_prices.py) ----- */
const toMin = s => { const [h,m]=s.split(":").map(Number); return h*60+m; };
function inWindow(day,mins,w){ if(!w) return false; if(!w.days.includes(day)) return false;
  return toMin(w.start)<=mins && mins<toMin(w.end); }
function offpeakWindows(gym){ const w=gym.offpeak_window; if(!w) return []; return Array.isArray(w)?w:[w]; }
let BANK_HOLIDAYS=null; // Set of "YYYY-MM-DD" (England & Wales), loaded from gov.uk
const ymd = d => d.getFullYear()+"-"+String(d.getMonth()+1).padStart(2,"0")+"-"+String(d.getDate()).padStart(2,"0");
function isBH(when){ return BANK_HOLIDAYS ? BANK_HOLIDAYS.has(ymd(when)) : false; }

function applicable(gym, when, conc){
  const day = WD[(when.getDay()+6)%7];           // JS Sun=0 -> our Mon=0
  const mins = when.getHours()*60 + when.getMinutes();
  const p = gym.payg;
  const bh = isBH(when);
  // magic slots
  let bestMagic=null;
  for(const s of (gym.magic_slots||[])){
    if(s.days.includes(day) && inWindow(day,mins,{days:s.days,start:s.start,end:s.end})){
      if(s.months && !s.months.includes(when.getMonth()+1)) continue;
      if(s.except_bank_holidays && bh) continue;
      const lbl=(s.label||"magic");
      if(lbl.toLowerCase().includes("concession") && !conc) continue;
      if(/board[- ]?only/i.test(lbl)) continue;   // board-only isn't standard entry — don't price it
      const full = /off[- ]?peak/i.test(lbl)
        ? (lbl+(s.prebook?" (pre-book)":""))
        : ("super off-peak ("+lbl+(s.prebook?", pre-book":"")+")");
      if(!bestMagic || s.price<bestMagic[0]) bestMagic=[s.price,full];
    }
  }
  if(bestMagic) return {price:bestMagic[0],label:bestMagic[1],kind:"magic"};
  const wins = offpeakWindows(gym);
  const isOff = wins.some(w=>inWindow(day,mins,w) && !(w.except_bank_holidays && bh));
  const concOnly = wins.some(w=>w.concession_only);
  let off,peak;
  if(conc){ off=p.concession_offpeak; peak=p.concession_peak; }
  else{ off=p.adult_offpeak; peak=p.adult_peak; if(concOnly) off=null; }
  if(isOff && off!=null) return {price:off,label:"off-peak",kind:"off"};
  if(peak!=null) return {price:peak,label:"peak",kind:"peak"};
  if(off!=null && wins.length===0) return {price:off,label:"off-peak",kind:"off"};
  return {price:null,label:"no data",kind:"none"};
}

function bestOnDay(gym, date, conc, step){
  step=step||15;
  const samples=[];
  for(let m=0;m<24*60;m+=step){
    const w=new Date(date.getFullYear(),date.getMonth(),date.getDate(),Math.floor(m/60),m%60);
    const r=applicable(gym,w,conc);
    if(r.price!=null) samples.push([m,r.price,r.label,r.kind]);
  }
  if(!samples.length) return null;
  const minp=Math.min(...samples.map(s=>s[1]));
  const first=samples.find(s=>s[1]===minp);
  let ranges=[],cur=null;
  for(const s of samples){ if(s[1]===minp){cur=cur?[cur[0],s[0]]:[s[0],s[0]];} else if(cur){ranges.push(cur);cur=null;} }
  if(cur) ranges.push(cur);
  const hhmm=x=>String(Math.floor(x/60)).padStart(2,"0")+":"+String(x%60).padStart(2,"0");
  let when;
  if(ranges.length===1 && ranges[0][0]===0 && ranges[0][1]>=24*60-step) when="any time";
  else when=ranges.map(r=>hhmm(r[0])+"-"+hhmm(Math.min(r[1]+step,24*60-1))).join("; ");
  return {price:minp,label:first[2],kind:first[3],when};
}
// All the distinct deal options available to `conc` on the given day (magic slots + off-peak),
// cheapest first. Used to show "also …" alternatives next to the headline cheapest price.
function slotsOnDay(gym, when, conc){
  const day = WD[(when.getDay()+6)%7], bh=isBH(when), p=gym.payg;
  let opts=[];
  (gym.magic_slots||[]).forEach(s=>{
    if(!s.days.includes(day)) return;
    if(s.months && !s.months.includes(when.getMonth()+1)) return;
    if(s.except_bank_holidays && bh) return;
    const lbl=s.label||'deal';
    if(/concession/i.test(lbl) && !conc) return;
    const allday = s.start==='00:00' && s.end==='23:59';
    opts.push({price:s.price, label:lbl.replace(' (concession)',''),
               when: allday?'any time':(s.start+'-'+s.end)+(s.prebook?' (pre-book)':'')});
  });
  const wins=offpeakWindows(gym);
  const w=wins.find(x=>x.days && x.days.includes(day) && !(x.except_bank_holidays && bh));
  const concOnly=wins.some(x=>x.concession_only);
  let off = conc ? (p.concession_offpeak ?? p.adult_offpeak) : p.adult_offpeak;
  if(!conc && concOnly) off=null;
  if(w && off!=null) opts.push({price:off, label:'off-peak', when:w.start+'-'+w.end});
  opts.sort((a,b)=>a.price-b.price);
  return opts;
}
// The contiguous window on the selected day during which the price applicable at
// `when` stays available (used by the specific-time mode of the Cheapest tab).
function windowForTime(gym, when, conc, step){
  step=step||15;
  const target=applicable(gym,when,conc);
  if(target.price==null) return '';
  const Y=when.getFullYear(),M=when.getMonth(),D=when.getDate();
  const at=m=>applicable(gym,new Date(Y,M,D,Math.floor(m/60),m%60),conc);
  const same=m=>{ const r=at(m); return r.price===target.price && r.label===target.label; };
  let m0=Math.floor((when.getHours()*60+when.getMinutes())/step)*step;
  let lo=m0, hi=m0;
  while(lo-step>=0 && same(lo-step)) lo-=step;
  while(hi+step<24*60 && same(hi+step)) hi+=step;
  const hhmm=x=>String(Math.floor(x/60)).padStart(2,"0")+":"+String(x%60).padStart(2,"0");
  if(lo<=0 && hi>=24*60-step) return "any time";
  return hhmm(lo)+"-"+hhmm(Math.min(hi+step,24*60-1));
}
let START = (window.localStorage && localStorage.getItem('startLoc')) || '';
let START_COORD = null;  // [lat,lng] once geocoded
function dirLinks(g){
  const dest=encodeURIComponent(g.address||g.postcode||(g.lat+","+g.lng));
  const origin = START ? "&origin="+encodeURIComponent(START) : "";
  return {gmap:"https://www.google.com/maps/dir/?api=1"+origin+"&destination="+dest+"&travelmode=transit"};
}
function haversineKm(a,b){
  const R=6371,dLat=(b[0]-a[0])*Math.PI/180,dLng=(b[1]-a[1])*Math.PI/180;
  const s=Math.sin(dLat/2)**2+Math.cos(a[0]*Math.PI/180)*Math.cos(b[0]*Math.PI/180)*Math.sin(dLng/2)**2;
  return R*2*Math.atan2(Math.sqrt(s),Math.sqrt(1-s));
}
function geocodeStart(cb){
  if(!START){START_COORD=null;cb&&cb();return;}
  fetch("https://nominatim.openstreetmap.org/search?format=json&limit=1&countrycodes=gb&q="+encodeURIComponent(START))
    .then(r=>r.json()).then(j=>{ START_COORD = j&&j[0] ? [parseFloat(j[0].lat),parseFloat(j[0].lon)] : null; cb&&cb(); })
    .catch(()=>{ START_COORD=null; cb&&cb(); });
}

/* ----- transit travel time from START via TfL Journey Planner (no key) ----- */
let TRAVEL={}, TRAVEL_START=null, TRAVEL_LOADING=false;
// Real-time ("now") departure when it's currently 8am-11pm; otherwise a stable
// weekday 10:00 departure so overnight numbers stay sensible.
function tflDepart(){
  const h=new Date().getHours();
  if(h>=8 && h<23) return "";   // no params => TfL plans from now (real-time)
  const d=new Date(); d.setHours(10,0,0,0);
  while(d.getDay()===0||d.getDay()===6) d.setDate(d.getDate()+1);
  const y=d.getFullYear(), m=String(d.getMonth()+1).padStart(2,"0"), da=String(d.getDate()).padStart(2,"0");
  return "date="+y+m+da+"&time=1000&timeIs=Departing";
}
function runLimited(items, worker, limit, done){
  let idx=0, active=0, finished=0;
  (function next(){
    while(active<limit && idx<items.length){
      const it=items[idx++]; active++;
      worker(it, ()=>{ active--; finished++; if(finished===items.length) done&&done(); else next(); });
    }
  })();
}
function ensureTravel(){
  if(!START || TRAVEL_START===START || TRAVEL_LOADING) return;
  TRAVEL_LOADING=true; TRAVEL={}; const forStart=START;
  runLimited(DATA.gyms,(g,doneOne)=>{
    const to=g.postcode?encodeURIComponent(g.postcode):(g.lat+","+g.lng);
    fetch("https://api.tfl.gov.uk/Journey/JourneyResults/"+encodeURIComponent(forStart)+"/to/"+to+"?"+tflDepart())
      .then(r=>r.ok?r.json():Promise.reject())
      .then(j=>{ const d=j.journeys&&j.journeys[0]?j.journeys[0].duration:null; if(d!=null) TRAVEL[g.id]=d; })
      .catch(()=>{}).finally(doneOne);
  },4,()=>{ TRAVEL_START=forStart; TRAVEL_LOADING=false; render(); });
}
function travelStr(g){
  if(!START) return '';
  if(TRAVEL_START!==START) return TRAVEL_LOADING?'…':'';
  const m=TRAVEL[g.id]; return m!=null? m+" min":'—';
}
function startTxt(){ if(!START) return 'your location (set it at the top)'; return /^[-0-9.]+,[-0-9.]+$/.test(START)?'your location':('"'+esc(START)+'"'); }
function setStart(v){
  START=(v||'').trim();
  try{localStorage.setItem('startLoc',START);}catch(e){}
  START_COORD=null; TRAVEL_START=null; TRAVEL_LOADING=false;
  if(START) geocodeStart(()=>render());
  render(); ensureTravel();
}
function useMyLocation(){
  if(!navigator.geolocation){ alert("This browser can't share your location."); return; }
  navigator.geolocation.getCurrentPosition(pos=>{
    const lat=pos.coords.latitude, lng=pos.coords.longitude;
    START=lat.toFixed(5)+","+lng.toFixed(5);
    START_COORD=[lat,lng];
    try{localStorage.setItem('startLoc',START);}catch(e){}
    const inp=el.getElementById("startloc"); if(inp) inp.value="My location";
    TRAVEL_START=null; TRAVEL_LOADING=false;
    render(); ensureTravel();
  }, err=>{ alert("Couldn't get your location: "+err.message+"\\nType a postcode instead."); }, {timeout:10000});
}

/* ----- personal notes & ratings per gym (saved locally + synced to account) ----- */
let NOTES={}, RATINGS={};
try{ NOTES=JSON.parse(localStorage.getItem('gymNotes')||'{}'); }catch(e){ NOTES={}; }
try{ RATINGS=JSON.parse(localStorage.getItem('gymRatings')||'{}'); }catch(e){ RATINGS={}; }
function localDataIsEmpty(){ return !Object.keys(NOTES||{}).length && !Object.keys(RATINGS||{}).length; }
function getLocalSyncTs(){ return +((window.localStorage&&localStorage.getItem('gymDataUpdated'))||0); }
function setLocalSyncTs(ts){ try{ localStorage.setItem('gymDataUpdated', String(ts||Date.now())); }catch(e){} }
function saveLocalData(ts){
  try{ localStorage.setItem('gymNotes', JSON.stringify(NOTES)); localStorage.setItem('gymRatings', JSON.stringify(RATINGS)); }catch(e){}
  setLocalSyncTs(ts);
}
/* If the browser already had notes from the old local-only version, mark them as
   needing upload the first time the user signs in. */
if(!localDataIsEmpty() && !getLocalSyncTs()) setLocalSyncTs(Date.now());
function openEditor(id){
  const g=DATA.gyms.find(x=>x.id===id);
  const gname = g?g.name:id;
  let tempR = RATINGS[id]||0;
  const overlay=document.createElement('div'); overlay.className='modal';
  overlay.innerHTML=
    `<div class="modal-box" role="dialog" aria-modal="true" aria-label="Edit note and rating">`+
    `<div class="modal-title">${esc(gname)}</div>`+
    `<label class="meta">Your rating</label>`+
    `<div class="starpick" id="starpick"></div>`+
    `<label class="meta" style="display:block;margin-top:12px">Your note</label>`+
    `<textarea id="noteInput" rows="4" placeholder="e.g. great cafe, quiet in the mornings…">${esc(NOTES[id]||'')}</textarea>`+
    (COMMUNITY_ENABLED?
      `<label class="toggle" style="margin-top:12px"><input type="checkbox" id="shareToggle"> Share my rating &amp; note publicly</label>`+
      `<label class="toggle" style="margin-top:4px"><input type="checkbox" id="anonToggle"> Post anonymously (hide my name)</label>`+
      `<div class="meta" id="shareHint" style="margin-top:2px"></div>`
      :'')+
    `<div class="modal-actions"><button id="nrSave">Save</button><button class="ghost" id="nrCancel">Cancel</button></div>`+
    `<div class="meta" style="margin-top:10px"><a href="#" id="nrReport">Report a wrong price for this gym</a></div>`+
    `</div>`;
  document.body.appendChild(overlay);
  overlay.querySelector('#nrReport').onclick=(e)=>{ e.preventDefault(); close(); openFeedback('price', gname); };
  let shareEl=null, hadPublic=false;
  if(COMMUNITY_ENABLED){
    shareEl=overlay.querySelector('#shareToggle');
    const anonEl=overlay.querySelector('#anonToggle');
    const hintEl=overlay.querySelector('#shareHint');
    // Anonymous still means "share" — ticking it turns sharing on; turning sharing off clears it.
    anonEl.addEventListener('change',()=>{ if(anonEl.checked) shareEl.checked=true; });
    shareEl.addEventListener('change',()=>{ if(!shareEl.checked) anonEl.checked=false; });
    if(FB && FB_USER){
      hintEl.textContent='Shows publicly with your Google name (or anonymously). Uncheck to remove.';
      fbReviewDoc(id).get().then(s=>{ if(s.exists){ hadPublic=true; shareEl.checked=true; if((s.data().name||'')==='Anonymous') overlay.querySelector('#anonToggle').checked=true; } }).catch(()=>{});
    } else {
      shareEl.disabled=true; hintEl.textContent='Sign in with Google (top of page) to share publicly.';
    }
  }
  const spEl=overlay.querySelector('#starpick');
  function drawStars(){
    spEl.innerHTML='';
    for(let k=1;k<=5;k++){
      const s=document.createElement('span');
      s.textContent = k<=tempR ? '★' : '☆';
      s.className='starbtn'; s.setAttribute('role','button'); s.tabIndex=0;
      s.setAttribute('aria-label', k+(k>1?' stars':' star'));
      s.onclick=()=>{ tempR = (tempR===k?0:k); drawStars(); };
      s.onkeydown=(e)=>{ if(e.key==='Enter'||e.key===' '){ e.preventDefault(); tempR=(tempR===k?0:k); drawStars(); } };
      spEl.appendChild(s);
    }
  }
  drawStars();
  const close=()=>{ document.removeEventListener('keydown',onKey); overlay.remove(); };
  function onKey(e){ if(e.key==='Escape') close(); }
  document.addEventListener('keydown',onKey);
  overlay.addEventListener('click',e=>{ if(e.target===overlay) close(); });
  overlay.querySelector('#nrCancel').onclick=close;
  overlay.querySelector('#nrSave').onclick=()=>{
    const v=overlay.querySelector('#noteInput').value.trim();
    if(v) NOTES[id]=v; else delete NOTES[id];
    if(tempR>=1&&tempR<=5) RATINGS[id]=tempR; else delete RATINGS[id];
    saveLocalData(); fbSave();
    if(COMMUNITY_ENABLED && FB && FB_USER && shareEl){
      if(shareEl.checked){
        if(!(tempR>=1&&tempR<=5)){ alert('Add a star rating before sharing publicly.'); return; }
        submitPublicReview(id, tempR, v, overlay.querySelector('#anonToggle').checked);
      } else if(hadPublic){
        removePublicReview(id);
      }
    }
    close(); render();
  };
  overlay.querySelector('#noteInput').focus();
}
window.openEditor=openEditor;
// Feedback / price reports go to this Google Form (opens in a new tab).
const FEEDBACK_FORM_URL="https://docs.google.com/forms/d/e/1FAIpQLSdWqho0guto0OG0XumGWdg8GeAbNj2ulPUFpVbSth3MdW83sA/viewform";
function openFeedback(){ if(FEEDBACK_FORM_URL) window.open(FEEDBACK_FORM_URL, "_blank", "noopener"); }
window.openFeedback=openFeedback;
function exportNotes(){
  const data=JSON.stringify({notes:NOTES,ratings:RATINGS});
  try{ navigator.clipboard.writeText(data); }catch(e){}
  window.prompt("Your notes + ratings backup (copied to clipboard). Paste this into ‘import’ on another device:", data);
}
function importNotes(){
  const s=window.prompt("Paste your notes + ratings backup here:");
  if(!s) return;
  try{ const j=JSON.parse(s);
    if(j.notes) NOTES=Object.assign({},NOTES,j.notes);
    if(j.ratings) RATINGS=Object.assign({},RATINGS,j.ratings);
    saveLocalData();
    fbSave(); render();
  }catch(e){ alert("That didn't look like a valid backup."); }
}
window.exportNotes=exportNotes; window.importNotes=importNotes;

/* ----- account sync (Firebase Auth + Firestore): private per-user sync ----- */
let FB=null, FB_USER=null, FB_UNSUB=null, FB_REMOTE_APPLYING=false, FB_LAST_ERROR='', FB_LAST_SYNCED=0;
function fmtSyncTime(ts){ return ts ? new Date(ts).toTimeString().slice(0,5) : ''; }
function fbConfigured(){ return window.FIREBASE_CONFIG && window.FIREBASE_CONFIG.apiKey && typeof firebase!=='undefined'; }
function fbDoc(){ return FB.db.collection('users').doc(FB_USER.uid); }
function fbInit(){
  if(!fbConfigured()){ updateAccountUI(); return; }
  try{ if(!firebase.apps.length) firebase.initializeApp(window.FIREBASE_CONFIG); }catch(e){ FB_LAST_ERROR=e.message; updateAccountUI(); return; }
  FB={auth:firebase.auth(), db:firebase.firestore()};
  FB.auth.onAuthStateChanged(function(u){
    if(FB_UNSUB){ FB_UNSUB(); FB_UNSUB=null; }
    FB_USER=u; FB_LAST_ERROR=''; updateAccountUI();
    if(u) fbStartSync();
  });
  loadGymStats();   // community averages are public — load them regardless of sign-in
}
function fbSignIn(){ if(!FB){alert("Account sync isn't set up on this site yet.");return;} FB.auth.signInWithPopup(new firebase.auth.GoogleAuthProvider()).catch(function(e){FB_LAST_ERROR=e.message; updateAccountUI(); alert(e.message);}); }
function fbSignOut(){ if(FB) FB.auth.signOut(); }
function applyRemoteData(j){
  NOTES=j.notes||{}; RATINGS=j.ratings||{};
  saveLocalData(j.updated||Date.now());
  FB_LAST_SYNCED=Date.now(); updateAccountUI();
  render();
}
function fbStartSync(){
  if(!FB||!FB_USER) return;
  fbDoc().get().then(function(s){
    const remote=s.exists?s.data():null;
    const remoteTs=remote&&remote.updated ? +remote.updated : 0;
    const localTs=getLocalSyncTs();
    if(remoteTs>localTs){
      applyRemoteData(remote);
    }else if(localTs>remoteTs || (!remote && !localDataIsEmpty())){
      fbSave();
    }else{
      FB_LAST_SYNCED=Date.now();
    }
    FB_UNSUB=fbDoc().onSnapshot(function(snap){
      if(!snap.exists) return;
      const j=snap.data()||{};
      const remoteTs=j.updated ? +j.updated : 0;
      if(remoteTs>getLocalSyncTs()){
        FB_REMOTE_APPLYING=true;
        applyRemoteData(j);
        FB_REMOTE_APPLYING=false;
      }
    }, function(e){ FB_LAST_ERROR=e.message; updateAccountUI(); });
    updateAccountUI();
  }).catch(function(e){ FB_LAST_ERROR=e.message; updateAccountUI(); });
}
function fbLoad(){ if(FB&&FB_USER) fbStartSync(); }
function fbSave(){
  if(!FB||!FB_USER||FB_REMOTE_APPLYING) return;
  const ts=Date.now();
  setLocalSyncTs(ts);
  fbDoc().set({notes:NOTES,ratings:RATINGS,updated:ts},{merge:true})
    .then(function(){ FB_LAST_ERROR=''; FB_LAST_SYNCED=Date.now(); updateAccountUI(); })
    .catch(function(e){ FB_LAST_ERROR=e.message; updateAccountUI(); console.error('Firestore sync failed:', e); });
}
function updateAccountUI(){
  const a=el.getElementById('account'); if(!a) return;
  if(!fbConfigured()){ a.innerHTML=''; return; }
  if(FB_USER){
    a.innerHTML='Synced as '+esc(FB_USER.email||'your account')
      +(FB_LAST_SYNCED?' · Last synced: '+fmtSyncTime(FB_LAST_SYNCED):'')
      +' · <a href="#" onclick="fbSignOut();return false;">sign out</a>'
      +(FB_LAST_ERROR?' · <span style="color:var(--warn)">sync error: '+esc(FB_LAST_ERROR)+'</span>':'');
  } else {
    a.innerHTML='<a href="#" onclick="fbSignIn();return false;">Sign in with Google</a> to sync your notes &amp; ratings across devices'+(FB_LAST_ERROR?' · <span style="color:var(--warn)">'+esc(FB_LAST_ERROR)+'</span>':'');
  }
}
window.fbSignIn=fbSignIn; window.fbSignOut=fbSignOut;

/* ----- community ratings (public, opt-in) ----- */
// Master switch for the community-ratings test feature.
// Set to false to hide it everywhere (averages, reviews, and the share toggles).
const COMMUNITY_ENABLED = true;
let PUB_STATS={};   // { gymId: {sum,count} }
function fbReviewDoc(gymId){ return FB.db.collection('reviews').doc(gymId).collection('users').doc(FB_USER.uid); }
function loadGymStats(){
  if(!COMMUNITY_ENABLED || !FB) return;
  FB.db.collection('gymStats').get()
    .then(function(s){ PUB_STATS={}; s.forEach(function(d){ PUB_STATS[d.id]=d.data(); }); render(); })
    .catch(function(){});
}
function submitPublicReview(gymId, rating, note, anon){
  if(!FB||!FB_USER) return;
  const name = anon ? 'Anonymous' : (FB_USER.displayName||'Anonymous');
  const revRef=fbReviewDoc(gymId), statRef=FB.db.collection('gymStats').doc(gymId);
  FB.db.runTransaction(function(tx){
    return tx.get(revRef).then(function(rev){ return tx.get(statRef).then(function(st){
      const old = rev.exists ? (rev.data().rating||0) : null;
      let sum = st.exists?(st.data().sum||0):0, count = st.exists?(st.data().count||0):0;
      if(old==null){ count+=1; sum+=rating; } else { sum += (rating-old); }
      tx.set(revRef,{rating:rating,note:(note||'').slice(0,500),name:name,updatedAt:Date.now()});
      tx.set(statRef,{sum:sum,count:count},{merge:true});
    }); });
  }).then(loadGymStats).catch(function(e){ console.error('review failed',e); alert("Couldn't share your review: "+e.message); });
}
function removePublicReview(gymId){
  if(!FB||!FB_USER) return;
  const revRef=fbReviewDoc(gymId), statRef=FB.db.collection('gymStats').doc(gymId);
  FB.db.runTransaction(function(tx){
    return tx.get(revRef).then(function(rev){ if(!rev.exists) return; return tx.get(statRef).then(function(st){
      const old=rev.data().rating||0;
      let sum=st.exists?(st.data().sum||0):0, count=st.exists?(st.data().count||0):0;
      sum-=old; count=Math.max(0,count-1);
      tx.delete(revRef); tx.set(statRef,{sum:sum,count:count},{merge:true});
    }); });
  }).then(loadGymStats).catch(function(){});
}
function communityCell(id){
  if(!COMMUNITY_ENABLED) return '';
  const st=PUB_STATS[id];
  if(!st||!st.count) return '';
  const avg=st.sum/st.count;
  return `<div class="meta">Community <span style="color:var(--accent2)">★ ${avg.toFixed(1)}</span> (${st.count}) · <a href="#" onclick="showReviews('${id}');return false;">reviews</a></div>`;
}
function showReviews(id){
  const g=DATA.gyms.find(x=>x.id===id), gname=g?g.name:id;
  if(!FB){ alert('Community reviews need the account feature configured on this site.'); return; }
  FB.db.collection('reviews').doc(id).collection('users').orderBy('updatedAt','desc').get().then(function(s){
    let rows=[];
    s.forEach(function(d){ const r=d.data();
      rows.push(`<div style="padding:8px 0;border-bottom:1px solid var(--line)"><div style="color:var(--accent2)">${stars(r.rating||0)} <span class="meta">${esc(r.name||'Anonymous')}</span></div>${r.note?`<div class="meta" style="color:var(--ink)">${esc(r.note)}</div>`:''}</div>`);
    });
    const overlay=document.createElement('div'); overlay.className='modal';
    overlay.innerHTML=`<div class="modal-box" role="dialog" aria-modal="true" aria-label="${esc(gname)} community reviews"><div class="modal-title">${esc(gname)} — community reviews</div>${rows.length?rows.join(''):'<div class="meta">No public reviews yet. Be the first — open a gym and tick “Share publicly”.</div>'}<div class="modal-actions"><button class="ghost" id="revClose">Close</button></div></div>`;
    document.body.appendChild(overlay);
    const prevFocus=document.activeElement;
    const close=()=>{ document.removeEventListener('keydown',onKey); overlay.remove();
      if(prevFocus&&prevFocus.focus) prevFocus.focus(); };
    function onKey(e){ if(e.key==='Escape') close(); }
    document.addEventListener('keydown',onKey);
    overlay.addEventListener('click',e=>{ if(e.target===overlay) close(); });
    overlay.querySelector('#revClose').onclick=close;
    overlay.querySelector('#revClose').focus();
  }).catch(function(e){ alert("Couldn't load reviews: "+e.message); });
}
window.showReviews=showReviews;
const esc=s=>String(s).replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
// Escapes text, then turns [label](https://url) into a link. Used for socials & class info.
function mdLinks(s){
  return esc(String(s)).replace(/\[([^\]]+)\]\((https?:[^)\s]+)\)/g,'<a href="$2" target="_blank" rel="noopener">$1</a>');
}
function stars(r){ return "★".repeat(r)+"☆".repeat(5-r); }
function noteCell(id){
  const n=NOTES[id], r=RATINGS[id];
  return communityCell(id)
    +(r?`<div class="meta" style="color:var(--accent2)">${stars(r)} (${r}/5)</div>`:"")
    +(n?`<div class="meta" style="color:var(--accent)">Note: ${esc(n)}</div>`:"")
    +`<a href="#" onclick="openEditor('${id}');return false;" class="meta">${(n||r)?'edit note & rating':'+ note / rating'}</a>`;
}

/* ----- helpers ----- */
const f=p=> p==null?"n/a":"£"+p.toFixed(2);
const el=document;
// Explicit index.html keeps links one-click navigable when the site is opened
// directly from disk; deployed canonical URLs remain the cleaner directory form.
const gymPage=g=>"gyms/"+g.id.replaceAll("_","-")+"/index.html";
function whenFromInputs(){
  const d=el.getElementById("date").value, t=el.getElementById("time").value||"12:00";
  const [Y,M,D]=d.split("-").map(Number), [h,m]=t.split(":").map(Number);
  return new Date(Y,M-1,D,h,m);
}
function activeFilters(){
  return {
    conc: el.getElementById("who").value === "conc",
    type: el.getElementById("type").value,
    zone: el.getElementById("zone").value,
    ac: el.getElementById("ac") ? el.getElementById("ac").value : ""
  };
}

/* ----- views ----- */
function viewCheapest(){
  const when=whenFromInputs(), {conc,type,zone,ac}=activeFilters();
  const mode=el.getElementById("mode").value;   // "day" or "time"
  let rows=[];
  for(const g of DATA.gyms){
    if(type && !g.type.includes(type)) continue;
    if(zone && String(g.zone)!==zone) continue;
    if(!acMatch(g,ac)) continue;
    if(mode==="day"){
      const b=bestOnDay(g,when,conc); if(!b) continue;
      const seen=new Set([b.price.toFixed(2)]); const alts=[];
      for(const o of slotsOnDay(g,when,conc)){ const k=o.price.toFixed(2);
        if(seen.has(k)) continue; seen.add(k); alts.push(o); if(alts.length>=2) break; }
      rows.push({...b,g,alts});
    }else{
      const r=applicable(g,when,conc); if(r.price==null) continue;
      rows.push({...r,when:windowForTime(g,when,conc),g});
    }
  }
  rows.sort((a,b)=>a.price-b.price);
  const who=conc?"student / concession":"adult";
  const bhBanner = isBH(when) ? `<div class="note" style="color:var(--accent2)">Heads up: ${when.toDateString()} is a UK bank holiday, so some off-peak, Magic Time and Bargain deals do not apply.</div>` : "";
  // Build header without a dedicated Air con column. AC status is appended to the Facilities column instead.
  let html=bhBanner+`<div class="card"><table class="cheapest-comparison"><thead><tr><th class="rank">#</th><th>Gym</th><th>Price</th><th>When to go</th><th>Travel</th><th>Facilities</th><th>Zone</th><th>Go</th></tr></thead><tbody>`;
  if(!rows.length) html+=`<tr><td colspan="8" class="note">No priced gyms match these filters.</td></tr>`;
  rows.forEach((r,i)=>{
    const d=dirLinks(r.g);
    html+=`<tr class="${i===0?'best':''}">
      <td class="rank">${i+1}</td>
      <td><a href="${gymPage(r.g)}" style="color:inherit;text-decoration:none">${r.g.name}</a> ${r.g.verified?'':'<span class="flag">verify</span>'}${openBadge(r.g,when)}${noteCell(r.g.id)}</td>
      <td><span class="price">${f(r.price)}</span> <span class="pill ${r.kind==='off'?'off':r.kind==='magic'?'magic':'peak'}">${r.label}</span>${(r.alts||[]).map(o=>`<div class="meta">also ${f(o.price)} ${esc(o.label)} · ${esc(o.when)}</div>`).join('')}</td>
      <td class="meta">${r.when||''}</td>
      <td class="meta">${travelStr(r.g)}</td>
      <td class="meta">${(r.g.facilities||'—')}${acDesc(r.g) ? ' · ' + acDesc(r.g) : ''}</td>
      <td>${r.g.zone}</td>
      <td class="meta"><a href="${gymPage(r.g)}">details</a> · <a href="${d.gmap}" target="_blank" rel="noopener">directions</a></td></tr>`;
  });
  const modeNote = mode==="day"
    ? `Cheapest ${who} price achievable on ${when.toDateString()} — "When to go" is the time window for that price. "also …" lines show other cheap options that day (e.g. an all-day rate when the cheapest is a short early window, or a board-only slot), so you can pick what actually works for you.`
    : `${who} price at ${el.getElementById("time").value||"12:00"} on ${when.toDateString()} — "When to go" is the window that price stays available.`;
  html+=`</tbody></table><div class="note">${modeNote} Travel = public-transport (bus/tube/rail) journey time from ${startTxt()}. "pre-book" = must book ahead. "verify" = price not confirmed at research. "directions" opens Google Maps public-transport routing.</div></div>`;
  return html;
}

function viewStudents(){
  const sig=g=> JSON.stringify([g.payg.concession_peak,g.payg.concession_offpeak,g.student_note||'']);
  let html=`<div class="card"><div class="tscroll"><table style="min-width:auto"><thead><tr><th>Gym</th><th>Conc. peak</th><th>Conc. off-peak</th><th>Notes</th></tr></thead><tbody>`;
  brandGroups().forEach(b=>{
    html+=`<tr><td colspan="4" style="background:var(--card2)"><span class="znum">${esc(b.brand)}</span></td></tr>`;
    collapseByBrand(b.sites, b.brand, sig).forEach(r=>{
      const g=r.rep;
      html+=`<tr><td><a href="${gymPage(g)}" style="color:inherit;text-decoration:none">${esc(r.label)}</a></td>
        <td class="price">${f(g.payg.concession_peak)}</td>
        <td class="price">${f(g.payg.concession_offpeak)}</td>
        <td class="meta">${esc(g.student_note||'')}</td></tr>`;
    });
  });
  html+=`</tbody></table></div><div class="note">Concession / student day-pass rates and student-specific deals, grouped by chain. Sites share one row when identical; a site is listed separately where it differs (e.g. Climbing District London Fields runs Student Saver on Fridays only). "conc" = concession.</div></div>`;
  if(DATA.student_promotions?.length){
    html+=`<div class="card"><div class="note" style="padding:12px 14px"><b>Group-wide student promotions</b></div>`;
    DATA.student_promotions.forEach(p=>{
      html+=`<div class="note" style="padding:2px 14px 10px">• <b>${p.offer}</b> — ${p.scope} <a href="${p.source}" target="_blank" rel="noopener">source</a></div>`;
    });
    html+=`</div>`;
  }
  return html;
}

function hoursSummary(g){
  const h=g.hours; if(!h) return '—';
  const days=["Mon","Tue","Wed","Thu","Fri","Sat","Sun"];
  let parts=[], i=0;
  while(i<7){
    const cur=h[days[i]]; let j=i;
    while(j+1<7 && JSON.stringify(h[days[j+1]])===JSON.stringify(cur)) j++;
    const lbl = i===j?days[i]:days[i]+"-"+days[j];
    parts.push(lbl+" "+(cur?cur[0]+"-"+cur[1]:"closed"));
    i=j+1;
  }
  return parts.join("; ")+(g.hours_assumed?" (approx)":"");
}
function openState(g, when){
  const h=g.hours; if(!h) return {open:null,txt:''};
  const day=WD[(when.getDay()+6)%7], t=h[day];
  const mins=when.getHours()*60+when.getMinutes();
  if(!t) return {open:false,txt:'closed today'};
  const o=toMin(t[0]), c=toMin(t[1]);
  if(mins<o) return {open:false,txt:'opens '+t[0]};
  if(mins>=c) return {open:false,txt:'closed'};
  return {open:true,txt:'open till '+t[1]};
}
function openBadge(g, when){
  const s=openState(g,when);
  if(s.open===null) return '';
  return ` <span class="pill ${s.open?'off':'peak'}">${s.txt}</span>`;
}
let ALL_SORT="chain";
function setAllSort(v){ ALL_SORT=v; render(); }
window.setAllSort=setAllSort;
// Peak price for the selected climber (adult or concession).
function peakOf(g,conc){ const p = conc ? g.payg.concession_peak : g.payg.adult_peak; return p==null?Infinity:p; }
// Cheapest off-peak / deal the selected climber can actually use.
function offBest(g,conc){
  let ps=[];
  const off = conc ? (g.payg.concession_offpeak ?? g.payg.adult_offpeak) : g.payg.adult_offpeak;
  if(off!=null) ps.push(off);
  for(const s of (g.magic_slots||[])){
    if(/concession/i.test(s.label||'') && !conc) continue;   // adults can't use concession-only slots
    ps.push(s.price);
  }
  return ps.length?Math.min(...ps):Infinity;
}
function viewAll(){
  const rank=b=> b==='London Climbing Centres'?0 : (b==='Independent'?2:1);
  const {conc,type,zone,ac}=activeFilters();
  const grouped = ALL_SORT==="chain";
  let gyms=[...DATA.gyms];
  if(type) gyms=gyms.filter(g=>g.type.includes(type));
  if(zone) gyms=gyms.filter(g=>String(g.zone)===zone);
  if(ac) gyms = gyms.filter(g => acMatch(g,ac));
  if(grouped){
    gyms.sort((a,b)=> rank(a.brand)-rank(b.brand) || a.brand.localeCompare(b.brand) || a.zone-b.zone || a.name.localeCompare(b.name));
  }else if(ALL_SORT==="peak"){
    gyms.sort((a,b)=> peakOf(a,conc)-peakOf(b,conc) || a.name.localeCompare(b.name));
  }else if(ALL_SORT==="offpeak"){
    gyms.sort((a,b)=> offBest(a,conc)-offBest(b,conc) || a.name.localeCompare(b.name));
  }else if(ALL_SORT==="distance"){
    const dist=g=> START_COORD ? haversineKm(START_COORD,[g.lat,g.lng]) : Infinity;
    gyms.sort((a,b)=> dist(a)-dist(b) || a.name.localeCompare(b.name));
  }
  const opt=(v,label)=>`<option value="${v}"${ALL_SORT===v?' selected':''}>${label}</option>`;
  const noLoc = ALL_SORT==="distance" && !START_COORD ? ` <span class="meta">— set your location above for distances</span>` : "";
  const whoTxt = conc ? 'student/concession' : 'adult';
  const priceHint = (ALL_SORT==="peak"||ALL_SORT==="offpeak") ? ` <span class="meta">— ${whoTxt} prices (change via “Climber” above)</span>` : "";
  const filterParts=[];
  if(type) filterParts.push(type);
  if(zone) filterParts.push('zone '+zone);
  if(ac) filterParts.push(ac==='full'?'full AC':'AC');
  const filterTxt=filterParts.join(' · ');
  const filterHint=filterTxt
    ? ` <span class="meta">— showing ${esc(filterTxt)} (change filters at the top)</span>`
    : ` <span class="meta">— tip: filter by Type, Zone or Air‑conditioning using the dropdowns at the top</span>`;
  const sortBar=`<div style="padding:10px 14px 0"><label class="meta" style="margin-right:6px">Sort</label>`+
    `<select aria-label="Sort gyms" onchange="setAllSort(this.value)" style="padding:6px 8px">`+
    opt("chain","By chain")+opt("peak","Cheapest — peak")+opt("offpeak","Cheapest — off-peak")+opt("distance","Nearest first")+
    `</select>${noLoc}${priceHint}${filterHint}</div>`;
  // Build header without a dedicated Air con column; AC status will be appended to the Facilities column.
  let html=`<div class="card">`+sortBar+`<div class="tscroll"><table class="gym-comparison"><thead><tr><th>Gym</th><th>Type</th><th>Peak</th><th>Off-peak &amp; cheap slots</th><th>Membership / passes</th><th>Facilities</th><th>Opening hours</th><th>Instagram</th><th>Address</th><th>Travel</th><th>Zone</th><th>Go</th></tr></thead><tbody>`;
  if(!gyms.length) html+=`<tr><td colspan="12" class="note">No gyms match the current Type/Zone filter — change them at the top of the page.</td></tr>`;
  let lastBrand=null;
  gyms.forEach(g=>{
    if(grouped && g.brand!==lastBrand){ lastBrand=g.brand;
      html+=`<tr><td colspan="12" style="background:var(--card2)"><span class="znum">${esc(g.brand)}</span></td></tr>`; }
    const cp=g.payg.concession_peak, d=dirLinks(g), m=g.membership||{};
    const memStr = m.full_monthly!=null ? ('£'+m.full_monthly+'/mo'+(m.concession_monthly!=null?(' (£'+m.concession_monthly+' conc)'):'')) : '';
    const memPass = [memStr, g.passes].filter(Boolean).map(esc).join('<br>') || '—';
    html+=`<tr>
      <td><a href="${gymPage(g)}" style="color:inherit;text-decoration:none">${g.name}</a> ${g.verified?'':'<span class="flag">verify</span>'}${noteCell(g.id)}</td>
      <td class="meta">${g.type.replace('+',' + ')}</td>
      <td class="price">${f(g.payg.adult_peak)}${cp!=null?`<div class="meta">${f(cp)} conc</div>`:''}</td>
      <td class="meta">${dealsCell(g)}</td>
      <td class="meta">${memPass}</td>
      <td class="meta">${(g.facilities||'—')}${acDesc(g) ? ' · ' + acDesc(g) : ''}</td>
      <td class="meta">${hoursSummary(g)}</td>
      <td class="meta">${igUrl(g)?`<a href="${igUrl(g)}" target="_blank" rel="noopener">@${esc(g.instagram)}</a>`:'—'}</td>
      <td class="meta">${esc(g.address||((g.area||'')+' '+(g.postcode||'')))}</td>
      <td class="meta">${travelStr(g)}</td>
      <td>${g.zone}</td>
      <td class="meta"><a href="${gymPage(g)}">details</a> · <a href="${d.gmap}" target="_blank" rel="noopener">directions</a></td></tr>`;
  });
  const groupNote = grouped ? 'Grouped by chain — London Climbing Centres first, independents last. ' : (ALL_SORT==='peak'?('Sorted by cheapest '+whoTxt+' peak price. ') : ALL_SORT==='offpeak'?('Sorted by cheapest '+whoTxt+' off-peak / deal price. ') : ALL_SORT==='distance'?'Sorted by distance from your location. ':'');
  html+=`</tbody></table></div><div class="note">On phones, each gym starts as a compact card; use “More details” for membership, facilities, hours, social links and address. Desktop keeps the full comparison table. Peak (adult, with concession) plus every off-peak and special cheap slot with its price and time window, membership/passes, facilities, opening hours, address, and public-transport travel time from ${startTxt()}. ${groupNote}Off-peak/deals cheapest first within each gym; "conc" = concession only; "~approx" = off-peak window assumed; hours "(approx)" = not individually confirmed.</div></div>`;
  return html;
}
function viewMemberships(){
  const rank=b=> b==='London Climbing Centres'?0 : (b==='Independent'?2:1);
  const gyms=[...DATA.gyms].sort((a,b)=>
    rank(a.brand)-rank(b.brand) || a.brand.localeCompare(b.brand) || a.name.localeCompare(b.name));
  const brands=[];
  gyms.forEach(g=>{ let b=brands.find(x=>x.brand===g.brand); if(!b){b={brand:g.brand,sites:[]};brands.push(b);} b.sites.push(g); });
  const sig=g=>{ const m=g.membership||{}; return JSON.stringify([m.full_monthly,m.concession_monthly,g.passes||null]); };
  const memRow=(label,g)=>{ const m=g.membership||{}, full=m.full_monthly, conc=m.concession_monthly;
    return `<tr>
      <td><a href="${gymPage(g)}" style="color:inherit;text-decoration:none">${label}</a></td>
      <td class="price">${full!=null?'£'+full.toFixed(2):'—'}</td>
      <td class="price">${conc!=null?'£'+conc.toFixed(2):'—'}</td>
      <td class="meta">${g.passes?esc(g.passes):'—'}</td></tr>`; };
  let html=`<div class="card"><div class="tscroll"><table style="min-width:0;width:auto"><thead><tr><th>Gym</th><th>Membership full / mo</th><th>Membership conc / mo</th><th>Multi-visit / punch cards</th></tr></thead><tbody>`;
  brands.forEach(b=>{
    html+=`<tr><td colspan="4" style="background:var(--card2)"><span class="znum">${esc(b.brand)}</span></td></tr>`;
    if(b.brand==='Independent'){ b.sites.forEach(g=> html+=memRow(esc(shortName(g)), g)); return; }
    const groups=[];
    b.sites.forEach(g=>{ const s=sig(g); let grp=groups.find(x=>x.sig===s); if(!grp){grp={sig:s,sites:[]};groups.push(grp);} grp.sites.push(g); });
    groups.forEach(grp=>{ const rep=grp.sites[0];
      let label;
      if(grp.sites.length===b.sites.length){ label='All sites'; }
      else if(grp.sites.length>1){ label=grp.sites.map(shortName).join(', '); }
      else { label=shortName(rep); }
      html+=memRow(esc(label), rep);
    });
  });
  html+=`</tbody></table></div><div class="note">Standard monthly rolling membership (unlimited climbing) plus multi-visit punch cards / bulk passes, grouped by chain. Sites in a chain share one row when their prices match; a site is listed separately only where it differs (e.g. Climbing Co Fulham, The Font Hounslow). "conc" = concession. "—" = not publicly listed. Annual memberships are usually cheaper than monthly; multi-site brands may include their other sites.</div></div>`;
  return html;
}

// An offer is past it if it says so, or if its published end date has gone by.
// Most offers have expires:null — gyms rarely publish one, and null means "no
// end date known", not "runs forever".
function promoExpired(p){
  if(p.status==='expired') return true;
  if(!p.expires) return false;
  const today=new Date().toISOString().slice(0,10);
  return p.expires < today;
}

function viewPromos(){
  const all=DATA.limited_promotions||[];
  const ps=all.filter(p=>!promoExpired(p));
  const hidden=all.length-ps.length;
  let html=`<div class="card"><table><thead><tr><th>Offer</th><th>Chain</th><th>Price</th><th>Details</th><th>Valid</th></tr></thead><tbody>`;
  if(!ps.length) html+=`<tr><td colspan="5" class="note">No limited-time offers running right now — check back soon, or see the standing deals under All gyms &amp; deals and Student discounts.</td></tr>`;
  ps.forEach(p=>{
    const flag = p.status==='unverified'?' <span class="flag">verify</span>':'';
    const ends = p.expires ? `<br><span class="meta" title="${esc(p.expires_source||'')}">ends ${esc(p.expires)}</span>` : '';
    html+=`<tr>
      <td><a href="${p.source}" target="_blank" rel="noopener" style="color:inherit">${esc(p.title)}</a>${flag}</td>
      <td class="meta">${esc(p.brand)}</td>
      <td class="price">${esc(p.price||'')}</td>
      <td class="meta">${esc(p.details||'')}</td>
      <td class="meta" style="color:var(--accent2)">${esc(p.valid||'')}${ends}</td></tr>`;
  });
  const note = hidden ? ` ${hidden} offer${hidden===1?'':'s'} past ${hidden===1?'its':'their'} published end date ${hidden===1?'is':'are'} hidden.` : '';
  html+=`</tbody></table><div class="note">Limited-time / seasonal offers (separate from the standing deals in "Off-peak & cheap slots"). The weekly price-check task re-reads these pages and flags new or expired offers.${note} Most gyms don't publish an end date, so an offer without one may still stop at any time — check the link before you buy. City Bouldering's June-only Stratford bargain has ended; their current standing deals (Bargain £7.50, Student Saver £9.50, BOGOF) are in the deals view. "verify" = not reconfirmed this session.</div></div>`;
  return html;
}

function igUrl(g){ return g.instagram ? ("https://www.instagram.com/"+g.instagram+"/") : null; }
function shortName(g){
  return g.name
    .replace(" (LCC)","")
    .replace("Climbing District - ","")
    .replace("City Bouldering ","")
    .replace("The Font ","Font ")
    .replace(" (City Bouldering)","");
}
// Group gyms into brand sections (LCC first, independents last).
function brandGroups(){
  const rank=b=> b==='London Climbing Centres'?0 : (b==='Independent'?2:1);
  const gyms=[...DATA.gyms].sort((a,b)=>
    rank(a.brand)-rank(b.brand) || a.brand.localeCompare(b.brand) || a.name.localeCompare(b.name));
  const brands=[];
  gyms.forEach(g=>{ let b=brands.find(x=>x.brand===g.brand); if(!b){b={brand:g.brand,sites:[]};brands.push(b);} b.sites.push(g); });
  return brands;
}
// Within a brand, merge sites whose sig() output is identical into one row.
// Returns [{label, zone, rep}]. Independents are never merged.
function collapseByBrand(sites, brand, sig){
  if(brand==='Independent') return sites.map(g=>({label:shortName(g),zone:g.zone,rep:g}));
  const groups=[];
  sites.forEach(g=>{ const s=sig(g); let grp=groups.find(x=>x.sig===s); if(!grp){grp={sig:s,sites:[]};groups.push(grp);} grp.sites.push(g); });
  return groups.map(grp=>{ const rep=grp.sites[0];
    if(grp.sites.length===sites.length) return {label:'All sites', zone:'all', rep};
    if(grp.sites.length>1) return {label:grp.sites.map(shortName).join(', '), zone:'—', rep};
    return {label:shortName(rep), zone:rep.zone, rep};
  });
}
// Combined off-peak + special ("magic") cheap slots for one gym, one line each, cheapest first.
// Collapse consecutive weekday runs: [Mon,Tue,Wed,Thu,Fri] -> "Mon-Fri"; [Mon,Fri] -> "Mon,Fri".
function fmtDays(days){
  const order=["Mon","Tue","Wed","Thu","Fri","Sat","Sun"];
  const ds=[...days].sort((a,b)=>order.indexOf(a)-order.indexOf(b));
  let runs=[],cur=null;
  for(const d of ds){ const i=order.indexOf(d);
    if(cur && i===cur.end+1){ cur.end=i; cur.days.push(d); }
    else { cur={start:i,end:i,days:[d]}; runs.push(cur); }
  }
  return runs.map(r=> r.days.length>=3 ? order[r.start]+"-"+order[r.end] : r.days.join(",")).join(",");
}
function dealsCell(g){
  let items=[];
  const ao=g.payg.adult_offpeak, co=g.payg.concession_offpeak;
  if(ao!=null || co!=null){
    const conc = activeFilters().conc;
    let main, extra='';
    if(ao==null){ main=co; extra=' conc'; }               // concession-only off-peak (adults have no off-peak tier)
    else if(co==null || co===ao){ main=ao; }              // same price for both
    else { main = conc ? co : ao; extra = conc ? ` (£${ao.toFixed(2)} adult)` : ` (£${co.toFixed(2)} conc)`; }
    // The off-peak rate is one price; merge all its windows onto a single line.
    const whens = offpeakWindows(g).map(w=>{
      let t=fmtDays(w.days)+' '+w.start+'-'+w.end;
      if(w.except_bank_holidays) t+=' (not bank hols)';
      return t;
    });
    if(whens.length) items.push({price:main,label:'off-peak',when:whens.join(', '),approx:g.offpeak_assumed,magic:false,extra});
  }
  // Merge slots with the same label + price (e.g. a gym's two Bargain windows) onto one line.
  const groups=[];
  for(const s of (g.magic_slots||[])){
    const allday = s.start==='00:00' && s.end==='23:59';
    let when = fmtDays(s.days)+(allday?' all day':(' '+s.start+'-'+s.end));
    if(s.except_bank_holidays) when += ' (not bank hols)';
    const label=(s.label||'magic').replace(' (concession)','');
    const prebook=!!s.prebook, conc=/concession/i.test(s.label||'');
    let grp=groups.find(x=>x.label===label && x.price===s.price && x.prebook===prebook && x.conc===conc);
    if(!grp){ grp={price:s.price,label,prebook,conc,whens:[]}; groups.push(grp); }
    grp.whens.push(when);
  }
  groups.forEach(m=>{
    items.push({price:m.price,label:m.label,when:m.whens.join(', '),approx:false,magic:true,
      extra:(m.prebook?' (pre-book)':'')+(m.conc?' conc':'')});
  });
  items.sort((a,b)=>a.price-b.price);
  if(!items.length) return '—';
  return items.map(d=> `<span class="pill ${d.magic?'magic':'off'}">${f(d.price)}</span> ${esc(d.label)} <span class="meta">${esc(d.when)}${d.extra}${d.approx?' ~approx':''}</span>`).join('<br>');
}
// Weekly calendar of special cheap slots (magic_slots) only, plus subscribe feeds.
const CAL_SITE="https://london-climbing-finder.pages.dev";
const CAL_ICS_ALL=CAL_SITE+"/london-climbing-deals-all.ics";
const CAL_ICS_NOSTU=CAL_SITE+"/london-climbing-deals-no-student.ics";
function calWebcal(u){ return u.replace(/^https?:/,"webcal:"); }
function calGoogleSub(u){
  try{ navigator.clipboard.writeText(u); }catch(e){}
  window.open("https://calendar.google.com/calendar/u/0/r/settings/addbyurl","_blank","noopener");
  alert("Calendar link copied.\n\nIn the Google Calendar page that just opened, paste it into “URL of calendar” and click “Add calendar”.");
}
window.calGoogleSub=calGoogleSub;
function viewCalendar(){
  const DAYS=["Mon","Tue","Wed","Thu","Fri","Sat","Sun"];
  const FULL=["Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday"];
  const idx={Mon:0,Tue:1,Wed:2,Thu:3,Fri:4,Sat:5,Sun:6};
  const byDay=[[],[],[],[],[],[],[]];
  DATA.gyms.forEach(g=>{
    (g.magic_slots||[]).forEach(s=>{
      const allday = s.start==='00:00' && s.end==='23:59';
      const when = allday ? 'all day' : (s.start+'-'+s.end);
      const label=(s.label||'deal').replace(' (concession)','');
      const conc=/concession/i.test(s.label||'');
      const extra=(s.prebook?' (pre-book)':'')+(conc?' conc':'')+(s.except_bank_holidays?' (not bank hols)':'');
      s.days.forEach(d=>{ if(d in idx) byDay[idx[d]].push({
        price:s.price,
        html:`<div class="calitem"><span class="price">${f(s.price)}</span> ${esc(label)}<br>`+
             `<a href="${g.prices_url}" target="_blank" rel="noopener" style="color:inherit">${esc(shortName(g))}</a>`+
             ` <span class="meta">${esc(when)}${esc(extra)}</span></div>`}); });
    });
  });
  const row=(name,ics)=>`<div style="margin:8px 0"><b>${name}</b><br>`+
    `<a class="btnlink" href="${calWebcal(ics)}">Apple / Outlook</a>`+
    `<a class="btnlink" href="#" onclick="calGoogleSub('${ics}');return false;">Google Calendar</a>`+
    `<a class="btnlink" href="${ics}" target="_blank" rel="noopener">Download .ics</a></div>`;
  let html=`<div class="card"><div style="padding:12px 14px">`+
    `<div style="font-weight:600;margin-bottom:4px">Subscribe to the deals calendar</div>`+
    `<div class="meta" style="margin-bottom:8px">Adds all the special cheap slots to your calendar as weekly repeating events, and updates automatically when they change.</div>`+
    row("All deals", CAL_ICS_ALL)+
    row("Without student-only deals", CAL_ICS_NOSTU)+
    `<div class="meta" style="margin-top:8px">Apple / Outlook opens your calendar to subscribe directly. Google copies the link and opens its “Add calendar from URL” page — just paste and click Add (Google has no one-tap subscribe for outside calendars). On phones, add it from Google Calendar on a computer; it then syncs to the app.</div>`+
    `</div></div>`;
  html+=`<div class="card"><div class="calgrid">`;
  DAYS.forEach((d,i)=>{
    byDay[i].sort((a,b)=>a.price-b.price);
    html+=`<div class="calcol"><h4>${FULL[i]}</h4>`;
    html+= byDay[i].length ? byDay[i].map(x=>x.html).join('') : '<div class="meta">—</div>';
    html+=`</div>`;
  });
  html+=`</div><div class="note">Preview of every special cheap slot (super off-peak, Magic Time, Bargain, Student Saver, BOGOF) by day, cheapest first. "conc" = students/concession only; "pre-book" = book ahead; "(not bank hols)" = excluded on bank holidays. Standard off-peak daytime pricing isn't shown here — see All gyms &amp; deals. Prices can change; tap a gym to confirm.</div></div>`;
  return html;
}
function viewWhatsOn(){
  let html=`<div class="card"><div class="tscroll"><table style="min-width:auto"><thead><tr><th>Gym</th><th>Regular socials &amp; sessions</th><th>Events</th><th>Route setting</th><th>Instagram</th></tr></thead><tbody>`;
  brandGroups().forEach(b=>{
    html+=`<tr><td colspan="5" style="background:var(--card2)"><span class="znum">${esc(b.brand)}</span></td></tr>`;
    b.sites.forEach(g=>{
      const ig=igUrl(g);
      const socials=(g.socials&&g.socials.length)?g.socials.map(mdLinks).join('<br>'):'—';
      html+=`<tr>
        <td>${esc(shortName(g))}</td>
        <td class="meta">${socials}</td>
        <td>${g.events_url?`<a href="${g.events_url}" target="_blank" rel="noopener">events ↗</a>`:'—'}</td>
        <td>${g.route_setting_url?`<a href="${g.route_setting_url}" target="_blank" rel="noopener">schedule ↗</a>`:'<span class="meta">on Instagram</span>'}</td>
        <td>${ig?`<a href="${ig}" target="_blank" rel="noopener">@${esc(g.instagram)}</a>`:'—'}</td></tr>`;
    });
  });
  html+=`</tbody></table></div><div class="note">Regular socials & sessions are recurring weekly things (e.g. women+ nights, board/coached socials); Events links to each gym's comps/workshops page. Times can vary by site and change seasonally (LCC in particular runs the same socials at different times per wall), so check the gym's events page or Instagram for the current slot. Chains share one events page (repeated per site). Only Mile End and The Reach publish a route-setting schedule online; every other gym posts its resets and events on Instagram — so the Instagram link is your live feed. Spotted a regular session we're missing? Use the feedback form in the footer.</div></div>`;
  return html;
}
function viewClasses(){
  const rank=b=> b==='London Climbing Centres'?0 : (b==='Independent'?2:1);
  const gyms=[...DATA.gyms].sort((a,b)=>
    rank(a.brand)-rank(b.brand) || a.brand.localeCompare(b.brand) || a.name.localeCompare(b.name));
  // one row per chain (same classes across sites); independents listed individually
  const rows=[], seen={};
  gyms.forEach(g=>{
    if(g.brand==='Independent'){ rows.push({label:g.name, ci:g.class_info, url:g.classes_url}); return; }
    if(seen[g.brand]) return; seen[g.brand]=1;
    let rep=g, label=g.brand;
    if(g.brand==='The Font'){ rep=DATA.gyms.find(x=>x.id==='the_font_wandsworth')||g; label='The Font (Wandsworth)'; }
    rows.push({label, ci:rep.class_info, url:rep.classes_url});
  });
  const lines=s=> s ? mdLinks(s).replace(/;\s*/g,'<br>') : 'Intro sessions &amp; courses';
  let html=`<div class="card"><table><thead><tr><th>Gym / chain</th><th>Climbing classes — prices, bundles &amp; offers</th><th>Book</th></tr></thead><tbody>`;
  rows.forEach(r=>{
    html+=`<tr>
      <td>${esc(r.label)}</td>
      <td class="meta">${lines(r.ci)}</td>
      <td>${r.url?`<a href="${r.url}" target="_blank" rel="noopener">details ↗</a>`:'—'}</td></tr>`;
  });
  html+=`</tbody></table><div class="note">Beginner intro sessions, multi-week technique courses and 1-to-1 coaching. Chains (LCC, City Bouldering, Climbing District, The Font) run the same classes across sites, so they're shown once. Intro sessions usually include shoe hire; some add free climbing time (City Bouldering's 2 weeks, LCC's NEW50 code). Prices shown where published; otherwise tap "details".</div></div>`;
  return html;
}

let MAP=null;
function viewMap(){
  const conc=activeFilters().conc, when=whenFromInputs();
  return `<div class="card">
    <div class="note">Set <b>Your location</b> at the top of the page to sort by distance and get travel times. Markers = every gym (green = cheapest all-day for your filters). Click a marker for price and directions. "directions" opens Google Maps public-transport routing (with live time).${START?'':' <span style="color:var(--accent2)">No location set yet.</span>'}</div>
    <div id="map"></div>
    <div id="maplist"></div>
  </div>`;
}
function renderMapList(){
  const {conc,type,zone,ac} = activeFilters(), when=whenFromInputs();
  const rows=DATA.gyms.filter(g=>{
    if(type && !g.type.includes(type)) return false;
    if(zone && String(g.zone)!==zone) return false;
    if(!acMatch(g,ac)) return false;
    return true;
  }).map(g=>{
    const b=bestOnDay(g,when,conc)||{price:null,when:""};
    const km=START_COORD?haversineKm(START_COORD,[g.lat,g.lng]):null;
    return {g,price:b.price,best:b,km};
  });
  rows.sort((a,b)=>(a.km==null?1e9:a.km)-(b.km==null?1e9:b.km));
  // Build header without a dedicated AC column; AC status will be appended to the gym description.
  let html=`<table><thead><tr><th>Gym</th><th>Dist</th><th>Travel</th><th>Cheapest today</th><th>Best time</th><th>Go</th></tr></thead><tbody>`;
  rows.forEach(r=>{ const d=dirLinks(r.g);
    // Append AC status to the gym meta description rather than using its own column
    html+=`<tr><td><a href="${gymPage(r.g)}" style="color:inherit;text-decoration:none">${r.g.name}</a><div class="meta">${r.g.area} · zone ${r.g.zone}${acDesc(r.g) ? ' · ' + acDesc(r.g) : ''}</div>${noteCell(r.g.id)}</td>
      <td class="meta">${r.km==null?'-':r.km.toFixed(1)+' km'}</td>
      <td class="meta">${travelStr(r.g)}</td>
      <td><span class="price">${f(r.price)}</span> ${r.best.label?`<span class="pill ${r.best.kind==='off'?'off':r.best.kind==='magic'?'magic':'peak'}">${r.best.label}</span>`:''}</td>
      <td class="meta">${r.best.when||''}</td>
      <td class="meta"><a href="${gymPage(r.g)}">details</a> · <a href="${d.gmap}" target="_blank" rel="noopener">directions</a></td></tr>`;
  });
  html+=`<tr><td colspan="6" class="note">Travel = public-transport (bus/tube/rail) journey time from ${startTxt()} (real-time now between 8am-11pm, otherwise a weekday 10:00 departure; loads live; — = no route found).</td></tr></tbody></table>`;
  const ml=el.getElementById("maplist"); if(ml){ ml.innerHTML=html; prepareResponsiveTables(ml); }
}
function initMap(){
  const box=el.getElementById("map"); if(!box) return;
  if(typeof L==="undefined"){ box.innerHTML='<div class="note">Map needs internet to load. Directions links still work.</div>'; renderMapList(); return; }
  if(MAP){ MAP.remove(); MAP=null; }
  MAP=L.map("map").setView([51.505,-0.09],11);
  L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png",{maxZoom:19,attribution:"© OpenStreetMap"}).addTo(MAP);
  // Apply the same filters to the map as the map list: type, zone and AC, in addition to who/time.
  const {conc,type,zone,ac} = activeFilters(), when=whenFromInputs();
  // Determine the cheapest price among gyms that pass the filters (for colouring markers)
  const priced=DATA.gyms.filter(g=>{
    if(type && !g.type.includes(type)) return false;
    if(zone && String(g.zone)!==zone) return false;
    if(!acMatch(g,ac)) return false;
    return true;
  }).map(g=>({g,b:bestOnDay(g,when,conc)})).filter(x=>x.b);
  const minp = priced.length ? Math.min(...priced.map(x=>x.b.price)) : Infinity;
  const pts=[];
  DATA.gyms.forEach(g=>{
    // Skip gyms that don't match the Type, Zone or AC filters
    if(type && !g.type.includes(type)) return;
    if(zone && String(g.zone)!==zone) return;
    if(!acMatch(g,ac)) return;
    const b=bestOnDay(g,when,conc);
    const cheap = b && b.price===minp;
    const d=dirLinks(g);
    const marker=L.circleMarker([g.lat,g.lng],{radius:7,color:cheap?"#5db07a":"#e2a13c",fillColor:cheap?"#5db07a":"#e2a13c",fillOpacity:.85,weight:1}).addTo(MAP);
    // Include AC information in the popup. If none, omit the AC line.
    const desc = acDesc(g);
    const acLine = desc ? `<br>AC: ${desc}` : '';
    marker.bindPopup(`<b>${g.name}</b><br>${g.area} · zone ${g.zone}<br>Cheapest today: <b>${f(b?b.price:null)}</b>${b&&b.when?" ("+b.when+")":""}${acLine}<br><a href="${gymPage(g)}">Details</a> · <a href="${d.gmap}" target="_blank" rel="noopener">Directions</a> · <a href="${g.prices_url}" target="_blank" rel="noopener">Official prices</a>`);
    pts.push([g.lat,g.lng]);
  });
  const drawStart=()=>{
    if(START_COORD){
      L.marker(START_COORD).addTo(MAP).bindPopup("Start: "+START);
      MAP.fitBounds(pts.concat([START_COORD]),{padding:[30,30]});
    } else { MAP.fitBounds(pts,{padding:[30,30]}); }
    renderMapList();
  };
  if(START && !START_COORD) geocodeStart(drawStart); else drawStart();
}

/* ----- wiring ----- */
let current="cheapest";
function prepareResponsiveTables(root=document){
  root.querySelectorAll("table").forEach(table=>{
    table.classList.add("responsive-table");
    const labels=[...table.querySelectorAll("thead th")].map(th=>th.textContent.trim()||"Item");
    table.querySelectorAll("tbody tr").forEach(row=>{
      const cells=[...row.children].filter(cell=>cell.tagName==="TD");
      if(cells.length===1 && cells[0].hasAttribute("colspan")){
        row.classList.add("section-row");
        return;
      }
      cells.forEach((cell,i)=>{
        const label=labels[i]||"Details";
        cell.dataset.label=label;
        if(table.classList.contains("gym-comparison")){
          cell.dataset.mobile=["Type","Membership / passes","Facilities","Opening hours","Instagram","Address","Zone"].includes(label)?"secondary":"primary";
        }
        const value=document.createElement("div");
        value.className="mobile-value";
        while(cell.firstChild) value.appendChild(cell.firstChild);
        cell.appendChild(value);
        if(!value.textContent.trim()) cell.classList.add("empty-value");
      });
      if(table.classList.contains("gym-comparison")){
        const gymName=(cells[0]?.querySelector("a")?.textContent||"this gym").trim();
        const action=document.createElement("td");
        action.className="mobile-expand-cell";
        const button=document.createElement("button");
        button.type="button"; button.className="mobile-expand ghost";
        button.textContent="More details";
        button.setAttribute("aria-label","Show more details for "+gymName);
        button.setAttribute("aria-expanded","false");
        button.addEventListener("click",()=>{
          const expanded=row.classList.toggle("expanded");
          button.textContent=expanded?"Fewer details":"More details";
          button.setAttribute("aria-label",(expanded?"Hide extra details for ":"Show more details for ")+gymName);
          button.setAttribute("aria-expanded",expanded?"true":"false");
        });
        action.appendChild(button); row.appendChild(action);
      }
    });
  });
}
function render(){
  const v={cheapest:viewCheapest,students:viewStudents,all:viewAll,memberships:viewMemberships,promos:viewPromos,classes:viewClasses,whatson:viewWhatsOn,calendar:viewCalendar,map:viewMap}[current];
  const panel=el.getElementById("view");
  panel.innerHTML=v();
  panel.classList.toggle("wide-view",current==="all");
  panel.setAttribute("aria-labelledby","tab-"+current);
  prepareResponsiveTables(panel);
  if(current==="map") initMap();
  ensureTravel();
}
function initZones(){
  const zs=[...new Set(DATA.gyms.map(g=>g.zone))].sort();
  const sel=el.getElementById("zone");
  zs.forEach(z=>{const o=document.createElement("option");o.value=z;o.textContent="Zone "+z;sel.appendChild(o);});
}
function setNow(){
  const n=new Date();
  el.getElementById("date").value=n.toISOString().slice(0,10);
  el.getElementById("time").value=n.toTimeString().slice(0,5);
}
function selectTab(view, focus){
  const tabs=[...document.querySelectorAll(".tab")];
  const t=tabs.find(x=>x.dataset.view===view)||tabs[0];
  tabs.forEach(x=>{ const on=x===t;
    x.classList.toggle("on",on);
    x.setAttribute("aria-selected",on?"true":"false");
    x.tabIndex = on?0:-1;
  });
  current=t.dataset.view;
  try{ localStorage.setItem("tab",current); }catch(e){}
  if(location.hash.slice(1)!==current){ history.replaceState(null,"","#"+current); }
  if(focus) t.focus();
  render();
}
document.querySelectorAll(".tab").forEach(t=>{
  t.setAttribute("role","tab");
  t.setAttribute("aria-selected", t.classList.contains("on")?"true":"false");
  t.tabIndex = t.classList.contains("on")?0:-1;
  t.addEventListener("click",()=>selectTab(t.dataset.view));
  t.addEventListener("keydown",e=>{
    const tabs=[...document.querySelectorAll(".tab")]; const i=tabs.indexOf(t);
    if(e.key==="Enter"||e.key===" "){ e.preventDefault(); selectTab(t.dataset.view); }
    else if(e.key==="ArrowRight"||e.key==="ArrowDown"){ e.preventDefault(); selectTab(tabs[(i+1)%tabs.length].dataset.view,true); }
    else if(e.key==="ArrowLeft"||e.key==="ArrowUp"){ e.preventDefault(); selectTab(tabs[(i-1+tabs.length)%tabs.length].dataset.view,true); }
    else if(e.key==="Home"){ e.preventDefault(); selectTab(tabs[0].dataset.view,true); }
    else if(e.key==="End"){ e.preventDefault(); selectTab(tabs[tabs.length-1].dataset.view,true); }
  });
});
// Re-render whenever any filter input changes, including air-con
["date","time","who","type","zone","ac","mode"].forEach(id => {
  const elId = el.getElementById(id);
  if(elId) elId.addEventListener("change", render);
});
el.getElementById("now").addEventListener("click",()=>{setNow();render();});
el.getElementById("startloc").value = START==="" ? "" : (/^[-0-9.]+,[-0-9.]+$/.test(START)?"My location":START);
el.getElementById("startloc").addEventListener("change",e=>setStart(e.target.value));
el.getElementById("geoloc").addEventListener("click",useMyLocation);

el.getElementById("meta").textContent =
  DATA.gyms.length+" gyms · prices last checked "+DATA._meta.last_research_date+" (may have changed since) · GBP, indicative day-pass entry";

/* Structured data (schema.org ItemList of gyms), built from DATA so it always
   matches gyms.json. Injected at runtime; Google renders JS. */
(function(){
  const items = DATA.gyms.map((g,i)=>({
    "@type":"ListItem", "position":i+1,
    "item":{
      "@type":"SportsActivityLocation", "name":g.name, "url":new URL(gymPage(g),location.href).href, "sameAs":g.website,
      "address":{"@type":"PostalAddress","streetAddress":g.address||"","postalCode":g.postcode||"","addressLocality":"London","addressCountry":"GB"},
      "geo":{"@type":"GeoCoordinates","latitude":g.lat,"longitude":g.lng}
    }
  }));
  const s=document.createElement("script"); s.type="application/ld+json";
  s.textContent=JSON.stringify({"@context":"https://schema.org","@type":"ItemList",
    "name":"London climbing gyms","numberOfItems":items.length,"itemListElement":items});
  document.head.appendChild(s);
})();
el.getElementById("foot").innerHTML =
  "\"Cheapest on this day\" ranks by the best price achievable any time that date (with the window to go); switch \"Cheapest mode\" to price a specific time. "+
  "Bank holidays are fetched live from gov.uk and some off-peak/Magic/Bargain deals are removed on those days. "+
  "Enter your postcode (or tap \"Use my location\") at the top to get live public-transport travel times to every gym and to sort by distance on the Map tab. "+
  "\"Super off-peak\" covers Bargain, Magic Time, Student Saver and BOGOF slots. "+
  "Notes and star ratings save locally and sync across devices when you sign in with Google. You can also <a href='#' onclick='exportNotes();return false;'>export</a> / <a href='#' onclick='importNotes();return false;'>import</a> them manually. "+
  "Prices marked <span class='flag'>verify</span> were not publicly confirmed at research time — click a gym to open its live price page. "+
  "Spotted a wrong price, a new deal, or have a suggestion? <a href='#' onclick='openFeedback(\"price\");return false;'>Report a price</a> · <a href='#' onclick='openFeedback(\"feedback\");return false;'>Share a deal / feedback</a>.";

function loadBankHolidays(){
  // Fetched live so no hard-coded list is kept. If offline/blocked, bank-holiday
  // exclusions simply aren't applied.
  fetch("https://www.gov.uk/bank-holidays.json")
    .then(r=>r.ok?r.json():Promise.reject())
    .then(j=>{ const ev=(j["england-and-wales"]||{}).events||[];
      BANK_HOLIDAYS=new Set(ev.map(e=>e.date)); render(); })
    .catch(()=>{ /* leave BANK_HOLIDAYS null; deals show without BH exclusions */ });
}
const _views=["cheapest","map","all","students","memberships","promos","classes","whatson","calendar"];
const _initialTab=(location.hash.slice(1)) || ((window.localStorage&&localStorage.getItem("tab"))||"") || "cheapest";
initZones(); setNow();
selectTab(_views.includes(_initialTab)?_initialTab:"cheapest");
loadBankHolidays(); fbInit();
