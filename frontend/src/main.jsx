import React,{useEffect,useMemo,useRef,useState} from 'react';
import{createRoot}from'react-dom/client';
import{motion,AnimatePresence}from'motion/react';
import logoFull from './logo-full.png?inline';
import logoMark from './logo-mark.png?inline';
import{LayoutDashboard,ClipboardList,Route,Package,ChartNoAxesCombined,Settings,PanelLeftClose,PanelLeftOpen,Search,Bell,Plus,CalendarDays,ChevronDown,ChevronRight,ArrowUpRight,Clock3,MapPin,Truck,CheckCircle2,AlertTriangle,Utensils,RefreshCw,Sun,Moon,MoreHorizontal,Filter, X,ChefHat,Activity,Boxes,FlaskConical,Trash2,Minus,Check,Navigation,TrendingUp,Users,ArrowDownRight,MessageCircle}from'lucide-react';
import './style.css';

const API='/api';
async function request(path,method='GET',body){const r=await fetch(API+path,{method,headers:body?{'Content-Type':'application/json'}:{},body:body?JSON.stringify(body):undefined});const data=await r.json().catch(()=>({}));if(!r.ok)throw new Error(data.error||data.message||`Request failed (${r.status})`);return data;}

// ---- amounts: the server keeps g / ml / pcs; people think in kg and litres
const fmtNum=v=>String(Math.round(Number(v)*100)/100);
const fmtQty=(v,unit)=>{const n=Number(v);if(unit==='g'&&Math.abs(n)>=1000)return fmtNum(n/1000)+' kg';if(unit==='ml'&&Math.abs(n)>=1000)return fmtNum(n/1000)+' L';return fmtNum(n)+' '+unit};
const inputUnit=u=>u==='g'?{label:'kg',factor:1000}:u==='ml'?{label:'L',factor:1000}:{label:u,factor:1};

// ---- Google Maps navigation links (stops[0] is the Kitchen)
// Google may limit how many stops one link accepts. If stops go missing when you test, lower this number.
const MAX_STOPS_PER_LINK=9;
const mapsPoint=s=>`${s.latitude},${s.longitude}`;
function buildNavLinks(stops){
 if(!stops||stops.length<2)return[];
 const kitchen=stops[0];
 const visits=stops.slice(1).filter(s=>isFinite(s.latitude)&&isFinite(s.longitude));
 const links=[];let start=kitchen;
 for(let i=0;i<visits.length;i+=MAX_STOPS_PER_LINK){
  const chunk=visits.slice(i,i+MAX_STOPS_PER_LINK);
  const isLast=i+MAX_STOPS_PER_LINK>=visits.length;
  const destination=isLast?kitchen:chunk[chunk.length-1];
  const middle=isLast?chunk:chunk.slice(0,-1);
  let url=`https://www.google.com/maps/dir/?api=1&origin=${encodeURIComponent(mapsPoint(start))}&destination=${encodeURIComponent(mapsPoint(destination))}&travelmode=driving`;
  if(middle.length)url+=`&waypoints=${middle.map(p=>encodeURIComponent(mapsPoint(p))).join('%7C')}`;
  links.push(url);start=destination;
 }
 return links;
}
function NavButtons({stops}){
 const links=buildNavLinks(stops);
 if(!links.length)return null;
 return <div className="nav-links">{links.map((url,i)=><a key={url} className="btn primary" href={url} target="_blank" rel="noopener noreferrer"><Navigation size={15}/>{links.length===1?'Start navigation in Google Maps':`Navigate: part ${i+1} of ${links.length}`}</a>)}</div>
}
const pad=n=>String(n).padStart(2,'0');
const dateText=d=>`${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}`;
const localStamp=(d=new Date())=>`${dateText(d)}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
const today=()=>dateText(new Date());
const fmtDate=s=>s?new Date(s).toLocaleString([], {month:'short',day:'numeric',hour:'2-digit',minute:'2-digit'}):'—';
const fmtTime=s=>s?new Date(s).toLocaleTimeString([], {hour:'2-digit',minute:'2-digit'}):'—';
const KITCHEN_NAME='Homemade Delicacies',ROAD_FACTOR=1.3,SPEED_KMH=20,STOP_MIN=5,WINDOW_MIN=15;
const clock=d=>d.toLocaleTimeString([], {hour:'numeric',minute:'2-digit'});
const defaultLeaveAt=()=>{const d=new Date();d.setSeconds(0,0);d.setMinutes(Math.ceil(d.getMinutes()/5)*5);return `${pad(d.getHours())}:${pad(d.getMinutes())}`};
function computeEtas(plan,date,leaveAt){
 const out={};const stops=(plan?.route?.stops||[]).slice(1);
 if(!/^\d\d:\d\d$/.test(leaveAt||''))return out;
 const [h,m]=leaveAt.split(':').map(Number),[Y,M,D]=date.split('-').map(Number);
 let t=new Date(Y,M-1,D,h,m,0,0).getTime();
 stops.forEach((s,i)=>{
  t+=Number(s.legKm||0)*ROAD_FACTOR/SPEED_KMH*3600000;
  const start=new Date(Math.floor(t/300000)*300000),end=new Date(start.getTime()+WINDOW_MIN*60000);
  (s.orderIds||[]).forEach(id=>{out[id]={stop:i+1,locality:s.locality,start,end}});
  t+=STOP_MIN*60000;
 });
 return out;
}
function waPhone(p){const d=String(p||'').replace(/\D/g,'');if(d.length===10)return '91'+d;if(d.length===11&&d[0]==='0')return '91'+d.slice(1);if(d.length===12&&d.startsWith('91'))return d;return null}
function waMessage(o,eta){
 const hello=`Hello ${o.customerName||'there'}, this is ${KITCHEN_NAME}.`;
 if(o.fulfilment==='DELIVERY'){
  if(eta)return `${hello} Your ${o.item} is on its way and should reach you between ${clock(eta.start)} and ${clock(eta.end)}. Please keep your phone nearby. Thank you!`;
  return `${hello} Your ${o.item} is being prepared for delivery. We will message you when it is on its way. Thank you!`;
 }
 return `${hello} Your ${o.item} will be ready for pickup at ${fmtTime(o.dueAt)}. Thank you!`;
}
const waUrl=(o,eta)=>{const p=waPhone(o.phone);return p?`https://wa.me/${p}?text=${encodeURIComponent(waMessage(o,eta))}`:null};
function WaButton({order,eta,small}){
 const url=waUrl(order,eta);
 if(!url)return <span className="wa-missing">No valid phone number</span>;
 return <a className={`btn teal ${small?'small':''}`} href={url} target="_blank" rel="noopener noreferrer"><MessageCircle size={small?15:17}/>{small?'Message':'Message on WhatsApp'}</a>;
}
const nav=[['overview','ChefBoard',LayoutDashboard],['orders','Orders',ClipboardList],['planner','Delivery planner',Route],['stock','Stock',Package],['analytics','Analytics',ChartNoAxesCombined],['settings','Settings',Settings]];
function App(){
 const [leaveAt,setLeaveAt]=useState(defaultLeaveAt),[view,setView]=useState('overview'),[collapsed,setCollapsed]=useState(()=>window.innerWidth<=760);
 const [date,setDate]=useState(today()),[orders,setOrders]=useState([]),[orderMeta,setOrderMeta]=useState({bloomHits:0,repeatsBlocked:0,falseAlarms:0}),[plan,setPlan]=useState(null),[stock,setStock]=useState(null),[localities,setLocalities]=useState([]),[kitchen,setKitchen]=useState(null),[health,setHealth]=useState(false),[loading,setLoading]=useState(true),[error,setError]=useState(''),[toast,setToast]=useState(null);
 const [search,setSearch]=useState(''),[filter,setFilter]=useState('All'),[modal,setModal]=useState(false),[selected,setSelected]=useState(null),[demoOpts,setDemoOpts]=useState({count:100,days:7,dup:10}),[stockDays,setStockDays]=useState(7),[pretendNow,setPretendNow]=useState(''),[confirmClear,setConfirmClear]=useState(false);
 const [form,setForm]=useState({customerName:'',phone:'',item:'',type:'DAILY_FOOD',fulfilment:'DELIVERY',dueAt:'',locality:'',family:'',quantity:1});
 const [saving,setSaving]=useState(false);
 useEffect(()=>{const fn=()=>setModal(true);document.addEventListener('new-order',fn);return()=>document.removeEventListener('new-order',fn)},[]);
 useEffect(()=>{if(!toast)return;const t=setTimeout(()=>setToast(null),3200);return()=>clearTimeout(t)},[toast]);
 async function refresh(d,n){
  d=typeof d==='string'?d:date;n=typeof n==='string'?n:pretendNow;
  const nowQ=n?`&now=${encodeURIComponent(n)}`:'';
  setLoading(true);setError('');
  try{
   const [h,o,p,s,l]=await Promise.all([
    request('/health'),request(`/orders?date=${d}${nowQ}`),request(`/plan?date=${d}&k=5${nowQ}`),request(`/stock?date=${d}&days=${stockDays}${nowQ}`),request('/localities')
   ]);
   setHealth(h.status==='ok');setOrders(o.orders||[]);setOrderMeta({bloomHits:o.bloomHits||0,repeatsBlocked:o.repeatsBlocked||0,falseAlarms:o.falseAlarms||0});setPlan(p);setStock(s);setLocalities(l.localities||[]);setKitchen(l.kitchen||null);
  }catch(e){setError(e.message);setHealth(false)}finally{setLoading(false)}
 }
 useEffect(()=>{refresh()},[date,stockDays,pretendNow]);
const etas=useMemo(()=>computeEtas(plan,date,leaveAt),[plan,date,leaveAt]);
 const stats=useMemo(()=>({total:orders.length,pending:orders.filter(o=>!o.made).length,late:orders.filter(o=>o.late).length,delivery:orders.filter(o=>o.fulfilment==='DELIVERY').length}),[orders]);
 const filtered=useMemo(()=>orders.filter(o=>{const q=search.toLowerCase();const matches=!q||[o.id,o.customerName,o.phone,o.item,o.locality].filter(Boolean).some(v=>String(v).toLowerCase().includes(q));const f=filter==='All'||(filter==='Late'?o.late:filter==='Delivery'?o.fulfilment==='DELIVERY':filter==='Pickup'?o.fulfilment==='PICKUP':o.type===filter);return matches&&f}),[orders,search,filter]);
 const notify=(msg,type='success')=>setToast({msg,type});
 const go=v=>{setView(v);if(window.innerWidth<=760)setCollapsed(true);setSelected(null)};
 async function createOrder(e){
  e.preventDefault();setSaving(true);
  try{const due=form.dueAt||localStamp(new Date(Date.now()+2*3600000));
   if(due<(pretendNow||localStamp()))throw new Error('That time has already passed. Please choose a day and time that is still to come.');
   const payload={...form,dueAt:due,...(form.family?{quantity:Number(form.quantity)}:{})};if(!form.family){delete payload.family;delete payload.quantity;}
   if(!payload.locality)delete payload.locality;
   if(pretendNow)payload.bookedAt=pretendNow;
   const res=await request('/orders','POST',payload);notify(res.message||'Order saved.');setModal(false);setForm({customerName:'',phone:'',item:'',type:'DAILY_FOOD',fulfilment:'DELIVERY',dueAt:'',locality:'',family:'',quantity:1});await refresh();
  }catch(e){notify(e.message,'error')}finally{setSaving(false)}
 }
 async function markMade(id){try{await request('/orders/made','POST',{id});notify('Order marked as made; stock updated.');setSelected(null);refresh()}catch(e){notify(e.message,'error')}}
 async function adjust(row,change){try{const r=await request('/stock/adjust','POST',{ingredientId:row.id,change});notify(`${row.name}: now ${fmtQty(r.onHand,row.unit)} on the shelf.`);refresh()}catch(e){notify(e.message,'error')}}
 async function resetStock(){try{await request('/stock/reset','POST',{});notify('Stock is back to the starting amounts.');refresh()}catch(e){notify(e.message,'error')}}
 async function loadDemo(){try{const r=await request(`/demo/generate?count=${demoOpts.count}&days=${demoOpts.days}&dupRate=${demoOpts.dup/100}`,'POST',{});notify(`${r.accepted} orders loaded · ${r.repeatsBlocked} repeats stopped`);const d=today();let n=pretendNow;if(n&&n.slice(0,10)!==d)n='';setPretendNow(n);setDate(d);setView('overview');setSelected(null);await refresh(d,n)}catch(e){notify(e.message,'error')}}
 async function doClear(){setConfirmClear(false);try{await request('/orders','DELETE');notify('All orders deleted.');refresh()}catch(e){notify(e.message,'error')}}
 const title=nav.find(n=>n[0]===view)?.[1]||'ChefBoard';
 return <div className="app-shell">
  <aside className={`sidebar ${collapsed?'collapsed':''}`}>
   <div className="brand">{collapsed?<img className="brand-mark-img" src={logoMark} alt="KitchenHQ" width="44" height="44"/>:<div className="brand-logo"><img src={logoFull} alt="KitchenHQ"/><span>Be the boss of your busy kitchen</span></div>}</div>
      <nav>{nav.map(([id,label,Icon])=><button key={id} className={`nav-item ${view===id?'active':''}`} onClick={()=>go(id)} title={collapsed?label:undefined}><Icon size={18}/>{!collapsed&&<span>{label}</span>}{id==='orders'&&!collapsed&&<small>{orders.length}</small>}</button>)}</nav>
   <div className="sidebar-bottom"><div className={`connection ${health?'online':'offline'}`}><i/>{!collapsed&&<span>{health?'Backend connected':'Backend unavailable'}</span>}</div><button className="collapse-btn" onClick={()=>setCollapsed(!collapsed)}>{collapsed?<PanelLeftOpen size={18}/>:<><PanelLeftClose size={18}/>Collapse sidebar</>}</button></div>
  </aside>
  <main className="main-area">
   <header className="topbar"><div className="top-left"><button className="mobile-menu icon-btn" aria-label="Open menu" onClick={()=>setCollapsed(!collapsed)}><PanelLeftOpen size={19}/></button><div className="date-chip"><CalendarDays size={17}/><input aria-label="Selected date" type="date" value={date} onChange={e=>setDate(e.target.value)}/></div></div><div className="top-actions"><button className="icon-btn" title="Refresh data" aria-label="Refresh data" onClick={()=>refresh()}><RefreshCw size={18}/></button><button className="btn primary" aria-label="New order" onClick={()=>setModal(true)}><Plus size={18}/><span className="btn-label">New order</span></button></div></header>
   <div className="page-wrap">
    {error&&<div className="error-banner"><AlertTriangle size={17}/>{error}<button onClick={()=>refresh()}>Retry</button></div>}
    {pretendNow&&<div className="pretend-banner"><Clock3 size={15}/>Pretending it is {new Date(pretendNow).toLocaleString([], {weekday:'short',day:'numeric',month:'short',hour:'numeric',minute:'2-digit'})}<button onClick={()=>setPretendNow('')}>Back to real time</button></div>}
    <AnimatePresence mode="wait">
     <motion.section key={view} className="page" initial={{opacity:0,y:7}} animate={{opacity:1,y:0}} exit={{opacity:0,y:-5}} transition={{duration:.18}}>
      {view==='overview'&&<Overview orders={orders} plan={plan} stock={stock} stats={stats} loading={loading} go={go} setSelected={setSelected} setModal={setModal} loadDemo={loadDemo} date={date}/>}
      {view==='orders'&&<Orders orders={filtered} total={orders.length} search={search} setSearch={setSearch} filter={filter} setFilter={setFilter} setModal={setModal} setSelected={setSelected}/>}
      {view==='planner'&&<Planner plan={plan} orders={orders} localities={localities} kitchen={kitchen} etas={etas} leaveAt={leaveAt} setLeaveAt={setLeaveAt}/>}
      {view==='stock'&&<Inventory stock={stock} days={stockDays} setDays={setStockDays} adjust={adjust} refresh={refresh} resetStock={resetStock}/>}
      {view==='analytics'&&<Analytics orders={orders} orderMeta={orderMeta} plan={plan} stock={stock}/>}
      {view==='settings'&&<SettingsPage demoOpts={demoOpts} setDemoOpts={setDemoOpts} loadDemo={loadDemo} askClear={()=>setConfirmClear(true)} pretendNow={pretendNow} setPretendNow={setPretendNow} date={date}/>}
     </motion.section>
    </AnimatePresence>
   </div>
  </main>
  <AnimatePresence>{modal&&<OrderModal form={form} setForm={setForm} localities={localities} families={stock?.families||[]} pretendNow={pretendNow} onClose={()=>setModal(false)} onSubmit={createOrder} saving={saving}/>}</AnimatePresence>
  <AnimatePresence>{confirmClear&&<ConfirmClear onCancel={()=>setConfirmClear(false)} onOk={doClear}/>}</AnimatePresence>
  <AnimatePresence>{selected&&<OrderDrawer order={selected} eta={etas[selected.id]} onClose={()=>setSelected(null)} onMade={markMade}/>}</AnimatePresence>
  <AnimatePresence>{toast&&<motion.div className={`toast ${toast.type}`} initial={{opacity:0,y:-12}} animate={{opacity:1,y:0}} exit={{opacity:0,y:-8}}>{toast.type==='error'?<AlertTriangle size={17}/>:<CheckCircle2 size={17}/>}{toast.msg}</motion.div>}</AnimatePresence>
 </div>
}
function PageHead({title,sub,action}){return <div className="page-head"><div><h1>{title}</h1>{sub&&<p>{sub}</p>}</div>{action}</div>}
function Metric({label,value,detail,tone=''}){return <div className={`metric-card ${tone}`}><span className="metric-label">{label}</span><strong>{value??'—'}</strong><small>{detail}</small></div>}
function Status({late,made}){return <span className={`status-pill ${made?'green':late?'red':'amber'}`}><i/>{made?'Made':late?'Overdue':'Open'}</span>}
function Panel({title,sub,action,children,className=''}){return <section className={`panel ${className}`}><div className="panel-head"><div><h3>{title}</h3>{sub&&<p>{sub}</p>}</div>{action}</div>{children}</section>}
const fmtDuration=m=>{m=Math.max(0,Math.round(m));if(m<60)return `${m} min`;if(m<1440){const h=Math.floor(m/60),r=m%60;return r?`${h} h ${r} min`:`${h} h`}const d=Math.floor(m/1440);return `${d} ${d===1?'day':'days'}`};
function NextUp({order,onOpen}){
 const late=order.late;
 return <section className={`next-up ${late?'late':''}`}>
  <div><p className="next-label">Do this next</p><h2>{order.item}</h2><p className="next-meta">{order.customerName}, {order.phone}. {order.fulfilment==='DELIVERY'?`Delivery to ${order.locality}.`:'Customer picks up.'}{order.family?` ${fmtNum(order.quantity)} ${order.familyUnit||''} of ${order.family}.`:''}</p></div>
  <div className="next-side"><strong className="next-due">{late?`Late by ${fmtDuration(-order.minutesLeft)}`:`Due in ${fmtDuration(order.minutesLeft)}`}</strong><span>{fmtDate(order.dueAt)}</span><button className="btn primary" onClick={()=>onOpen(order)}>Open order</button></div>
 </section>
}
function Overview({orders,plan,stock,stats,loading,go,setSelected,setModal,loadDemo,date}){
 const open=orders.filter(o=>!o.made);
 const urgent=[...open].sort((a,b)=>(b.score||0)-(a.score||0)).slice(0,5);
 const stops=plan?.route?.stops||[];
 const visits=stops.slice(1);
 const shortCount=(stock?.counts?.short||0)+(stock?.counts?.out||0);
 const summary=!orders.length?'Nothing is booked for this day yet.':`${open.length} ${open.length===1?'order is':'orders are'} still to make${stats.late?`, ${stats.late} of them overdue`:''}.${shortCount?` ${shortCount} ${shortCount===1?'ingredient is':'ingredients are'} short.`:''}`;
 const totalIngredients=stock?.rows?.length||0;
 return <><PageHead title={new Date(date+'T12:00:00').toLocaleDateString([], {weekday:'long',day:'numeric',month:'long'})} sub={summary}/>
 {!loading&&!orders.length&&<section className="panel start-panel"><h2>Add your first order</h2><p>Type in a real order, or load sample orders to see how ChefBoard, the delivery planner and stock work together.</p><div className="start-actions"><button className="btn primary" onClick={()=>setModal(true)}><Plus size={18}/>New order</button><button className="btn secondary" onClick={loadDemo}><FlaskConical size={17}/>Load sample orders</button></div></section>}
 {urgent[0]&&<NextUp order={urgent[0]} onOpen={setSelected}/>}
 <div className="metric-grid">{loading?<><div className="skeleton"/><div className="skeleton"/><div className="skeleton"/><div className="skeleton"/></>:<><Metric label="Orders" value={stats.total} detail="on this day"/><Metric label="Still to make" value={stats.pending} detail="not marked as made" tone={stats.pending?'orange':''}/><Metric label="Overdue" value={stats.late} detail="past their due time" tone={stats.late?'red':''}/><Metric label="Deliveries" value={stats.delivery} detail={`${plan?.pickupCount||0} for pickup`}/></>}</div>
 <div className="content-grid two-one">
  <Panel title="Most urgent orders" sub="Highest urgency first" action={<button className="text-btn" onClick={()=>go('orders')}>All orders <ArrowUpRight size={16}/></button>}>
   {urgent.length?<div className="table-wrap"><table><thead><tr><th>Order</th><th>Due</th><th>Urgency</th><th>Status</th></tr></thead><tbody>{urgent.map(o=><tr key={o.id} onClick={()=>setSelected(o)} className="click-row"><td><b>{o.item}</b><span className="cell-sub">{o.id}, {o.customerName}</span></td><td>{fmtTime(o.dueAt)}<span className="cell-sub">{o.minutesLeft<0?`${fmtDuration(-o.minutesLeft)} late`:`in ${fmtDuration(o.minutesLeft)}`}</span></td><td><span className={`priority ${o.late?'late':o.minutesLeft<60?'high':'normal'}`}>{o.late?'Critical':o.minutesLeft<60?'High':'Normal'}</span></td><td><Status late={o.late} made={o.made}/></td></tr>)}</tbody></table></div>:<Empty title="No open orders for this day" text="Orders you add will appear here, most urgent first."/>}
  </Panel>
  <Panel title="Stock" sub="Against orders already booked" action={<button className="text-btn" onClick={()=>go('stock')}>Open stock <ArrowUpRight size={16}/></button>}>
   <p className="big-line"><b>{stock?.counts?.ok??'—'}</b>of {totalIngredients} ingredients are fine</p>
   <div className="stock-legend"><span><i className="dot teal"/>Fine <b>{stock?.counts?.ok??0}</b></span><span><i className="dot amber"/>Running low <b>{stock?.counts?.low??0}</b></span><span><i className="dot red"/>Short or empty <b>{shortCount}</b></span></div>
   {(stock?.rows||[]).filter(r=>r.level!=='OK').slice(0,3).map(r=><div className="alert-row" key={r.id}><AlertTriangle size={16}/><span>{r.name}</span><b>{r.available<0?`short by ${fmtQty(-r.available,r.unit)}`:`${fmtQty(r.available,r.unit)} free`}</b></div>)}
   {!stock?.rows?.some(r=>r.level!=='OK')&&<div className="inline-success"><CheckCircle2 size={17}/>No stock problems in this window</div>}
  </Panel>
 </div>
 <div className="content-grid two-one">
  <Panel title="Deliveries" sub="Shortest sensible round trip" action={<button className="text-btn" onClick={()=>go('planner')}>Open planner <ArrowUpRight size={16}/></button>}>
   <div className="route-stats"><div><strong>{plan?.deliveryCount??0}</strong><span>deliveries</span></div><div><strong>{visits.length}</strong><span>stops</span></div><div><strong>{Number(plan?.route?.totalKm||0).toFixed(1)} km</strong><span>round trip</span></div></div>
   <NavButtons stops={stops}/>
   <div className="mini-route">{visits.slice(0,4).map((s,i)=><div className="mini-stop" key={s.locality}><span>{i+1}</span><b>{s.locality}</b><small>{s.orderIds?.length||0} {(s.orderIds?.length||0)===1?'order':'orders'}</small></div>)}{!visits.length&&<Empty title="No deliveries" text="Delivery orders for this day will appear here."/>}</div>
  </Panel>
  <Panel title="Pickups" sub="Customers collecting from the kitchen">
   {(plan?.pickups||[]).slice(0,4).map(o=><div className="pickup-row" key={o.id}><div className="avatar">{(o.customerName||'?').slice(0,1)}</div><div><b>{o.customerName}</b><span>{o.item}</span></div><strong>{fmtTime(o.dueAt)}</strong></div>)}
   {!plan?.pickups?.length&&<Empty title="No pickups" text="Pickup orders for this day will appear here."/>}
  </Panel>
 </div>
 </>
}
function Orders({orders,total,search,setSearch,filter,setFilter,setModal,setSelected}){
 const filters=['All','Late','Delivery','Pickup','CATERING','BAKERY','DAILY_FOOD'];
 return <><PageHead title="Orders" sub={`${total} ${total===1?'order':'orders'} on this day. Search by name, phone, item or place.`}/>
 <Panel title="Order register" sub="Click an order to see its details.">
  <div className="toolbar"><div className="searchbox"><Search size={17}/><input placeholder="Search customer, phone, item or locality..." value={search} onChange={e=>setSearch(e.target.value)}/>{search&&<button onClick={()=>setSearch('')}><X size={14}/></button>}</div><div className="filter-select"><Filter size={15}/><select value={filter} onChange={e=>setFilter(e.target.value)}>{filters.map(f=><option key={f}>{f}</option>)}</select></div></div>
  <div className="table-wrap"><table><thead><tr><th>Order</th><th>Customer</th><th>Item / type</th><th>Fulfilment</th><th>Due</th><th>Urgency score</th><th>Status</th><th/></tr></thead><tbody>{orders.map(o=><tr className="click-row" key={o.id} onClick={()=>setSelected(o)}><td><b>{o.id}</b></td><td><b>{o.customerName}</b><span className="cell-sub">{o.phone}</span></td><td>{o.item}<span className="cell-sub">{o.type?.replace('_',' ')}</span></td><td><span className="fulfil">{o.fulfilment==='DELIVERY'?<Truck size={14}/>:<Utensils size={14}/>} {o.fulfilment}</span><span className="cell-sub">{o.locality||'Kitchen pickup'}</span></td><td>{fmtDate(o.dueAt)}</td><td><b>{Number(o.score||0).toFixed(1)}</b><span className="cell-sub">{o.minutesLeft<0?`${Math.abs(o.minutesLeft)}m late`:`${o.minutesLeft}m remaining`}</span></td><td><Status late={o.late} made={o.made}/></td><td><ChevronRight size={16}/></td></tr>)}</tbody></table>{!orders.length&&<Empty title="No matching orders" text="Try changing your filters or create a new order."/>}</div>
 </Panel></>
}
function Planner({plan,orders,localities,kitchen,etas,leaveAt,setLeaveAt}){
 const byId=Object.fromEntries((orders||[]).map(o=>[o.id,o]));
 const route=plan?.route||{};
 const stops=route.stops||[];
 const visits=stops.slice(1);
 const saving=route.arrivalOrderKm>route.totalKm+0.05?Math.round((route.arrivalOrderKm-route.totalKm)/route.arrivalOrderKm*100):0;
 return <><PageHead title="Delivery planner" sub="The shortest sensible round trip for the day's deliveries."/>
 <div className="metric-grid three"><Metric label="Delivery orders" value={plan?.deliveryCount??0} detail="Scheduled for this date" icon={Truck}/><Metric label="Stops" value={visits.length} detail="Grouped by locality" icon={MapPin} tone="orange"/><Metric label="Round trip" value={`${Number(route.totalKm||0).toFixed(2)} km`} detail={saving>=1?`${saving}% shorter than arrival order`:'Straight-line distance from kitchen'} icon={Route} tone="gold"/></div>
 <div className="planner-layout"><Panel title="Delivery route" sub="Pins are numbered in delivery order." className="map-panel"><LeafletMap plan={plan} orders={orders} localities={localities} kitchen={kitchen}/><div className="map-legend"><span><i className="swatch ok"/>Plenty of time</span><span><i className="swatch soon"/>Due soon</span><span><i className="swatch late"/>Late</span></div></Panel>
 <Panel title="Delivery order" sub={`${visits.length} locality stops · grouped delivery orders`}><label className="leave-box"><span>Leaving the kitchen at</span><input type="time" value={leaveAt} onChange={e=>setLeaveAt(e.target.value)}/></label><NavButtons stops={stops}/><div className="route-list">{visits.map((s,i)=><div className="route-item" key={s.locality}><div className="route-number">{i+1}</div><div className="route-info"><b>{s.locality}</b>{etas[(s.orderIds||[])[0]]&&<span className="eta">Arrive about {clock(etas[s.orderIds[0]].start)} to {clock(etas[s.orderIds[0]].end)}</span>}<div className="stop-orders">{(s.orderIds||[]).map(id=>byId[id]&&<div className="stop-order" key={id}><span>{byId[id].customerName}, {byId[id].item}</span><WaButton order={byId[id]} eta={etas[id]} small/></div>)}</div></div><div className="route-km">{Number(s.legKm||0).toFixed(2)} km</div></div>)}{!visits.length&&<Empty title="Route is empty" text="There are no delivery orders on this date."/>}</div>{visits.length>0&&<div className="route-total"><span>Back to the kitchen</span><b>{Number(route.returnKm||0).toFixed(2)} km</b></div>}</Panel></div>
 <Panel title="Algorithm comparison" sub="Round-trip distance for each method, in straight-line km. Shorter is better."><div className="comparison-grid">{[['Our route (MST tour)',route.totalKm],['Prim MST (lower bound)',route.mstKm],['Nearest neighbour',route.nearestNeighbourKm],['Arrival order',route.arrivalOrderKm],['Held–Karp exact',route.exactKm]].map(([l,v])=><div className="compare-card" key={l}><span>{l}</span><b>{v==null?'Not computed':`${Number(v).toFixed(3)} km`}</b></div>)}</div><p className="muted-note">Arrival times are estimates: 20 km/h on roads about 30% longer than the straight line, plus 5 minutes at each stop. They are shown as a 15-minute window.</p><div className="guarantee">{route.withinGuarantee?<CheckCircle2 size={16}/>:<Activity size={16}/>} {route.withinGuarantee?'The route is within 2 times the spanning tree, so within 2 times the best possible. That is a guarantee, not a claim that it is the very best.':'Approximation guarantee status is reported by the Java engine.'}</div></Panel>
 </>
}
function LeafletMap({plan,orders,localities,kitchen}){
 const host=useRef(null);
 useEffect(()=>{
  const L=window.L;if(!host.current||!L||!kitchen)return;
  const map=L.map(host.current,{zoomControl:true,scrollWheelZoom:false,preferCanvas:true});
  map.attributionControl.setPrefix(false);
  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:18,attribution:'&copy; OpenStreetMap contributors'}).addTo(map);
  const bounds=[[kitchen.latitude,kitchen.longitude]];
  localities.forEach(place=>L.circleMarker([place.latitude,place.longitude],{radius:4,color:'#fff',weight:1,fillColor:'#b5c8c0',fillOpacity:.8}).bindTooltip(place.name).addTo(map));
  const visits=(plan?.route?.stops||[]).slice(1);
  const byId={};orders.forEach(o=>{byId[o.id]=o});
  const levelOf=s=>{let worst='ok';(s.orderIds||[]).forEach(id=>{const o=byId[id];if(!o)return;if(o.late)worst='late';else if(o.minutesLeft<120&&worst!=='late')worst='soon'});return worst};
  if(visits.length){
   const line=visits.map(s=>[s.latitude,s.longitude]);
   L.polyline([[kitchen.latitude,kitchen.longitude],...line,[kitchen.latitude,kitchen.longitude]],{color:'#fd7d00',weight:4,opacity:.9,dashArray:'8 7'}).addTo(map);
   visits.forEach((s,i)=>{bounds.push([s.latitude,s.longitude]);const marker=L.marker([s.latitude,s.longitude],{icon:L.divIcon({className:`kh-map-pin ${levelOf(s)}`,html:`<span>${i+1}</span>`,iconSize:[32,32],iconAnchor:[16,16]})}).addTo(map);marker.bindTooltip(`${s.locality} · ${(s.orderIds||[]).length} orders`);});
  }
  const kitchenMarker=L.marker([kitchen.latitude,kitchen.longitude],{icon:L.divIcon({className:'kh-kitchen-pin',html:'<span>⌂</span>',iconSize:[38,38],iconAnchor:[19,19]})}).addTo(map);kitchenMarker.bindTooltip('Kitchen');
  map.fitBounds(bounds,{padding:[35,35]});
  const timer=setTimeout(()=>map.invalidateSize(),100);
  return()=>{clearTimeout(timer);map.remove()};
 },[plan,orders,localities,kitchen]);
 if(typeof window!=='undefined'&&!window.L)return <div className="empty"><b>The map could not be loaded</b><span>Please reload the page.</span></div>;
 return <div className="leaflet-wrap"><div ref={host} className="leaflet-host"/><div className="map-footnote">Straight lines between stops; map pictures need an internet connection.</div></div>
}
function AdjustCell({row,adjust}){
 const [amount,setAmount]=useState('');
 const u=inputUnit(row.unit);
 const ok=Number(amount)>0;
 const apply=sign=>{if(!ok)return;adjust(row,sign*Number(amount)*u.factor);setAmount('')};
 return <div className="adjust-box"><input type="number" min="0" step="any" inputMode="decimal" placeholder="Amount" aria-label={`Amount of ${row.name} in ${u.label}`} value={amount} onChange={e=>setAmount(e.target.value)}/><em>{u.label}</em><button type="button" disabled={!ok} onClick={()=>apply(1)} title="I bought more"><Plus size={13}/>Bought</button><button type="button" disabled={!ok} onClick={()=>apply(-1)} title="Used some"><Minus size={13}/>Used</button></div>
}
function Inventory({stock,days,setDays,adjust,refresh,resetStock}){
 const rows=stock?.rows||[];
 const problems=(stock?.counts?.short||0)+(stock?.counts?.out||0),lows=stock?.counts?.low||0;
 return <><PageHead title="Stock" sub="What is on the shelf, what booked orders will use, and what to buy. Amounts are sample numbers; usage is a rough average recipe per product." action={<button className="btn secondary" onClick={()=>refresh()}><RefreshCw size={16}/>Refresh stock</button>}/>
 {stock&&<div className={`stock-banner ${problems?'bad':lows?'warn':'good'}`}>{problems?`${problems} ${problems===1?'supply is':'supplies are'} not enough for the booked orders. See the shopping list below.`:lows?`${lows} ${lows===1?'supply is':'supplies are'} running low. Time to plan a shopping trip.`:stock.linkedOrders?`Everything is in stock for the ${stock.linkedOrders} booked ${stock.linkedOrders===1?'order':'orders'}.`:'Everything is in stock. No booked orders are using the stock yet.'}</div>}
 <div className="metric-grid"><Metric label="Ingredients tracked" value={rows.length} detail="Across all stock groups" icon={Boxes}/><Metric label="Fine" value={stock?.counts?.ok??0} detail="Above low-stock threshold" icon={CheckCircle2}/><Metric label="Running low" value={stock?.counts?.low??0} detail="Below preferred level" icon={AlertTriangle} tone="gold"/><Metric label="Short / out" value={problems} detail="May affect booked orders" icon={Package} tone="red"/></div>
 <Panel title="Stock position" sub={`${stock?.linkedOrders||0} linked orders · Forecast window`} action={<select className="compact-select" value={days} onChange={e=>setDays(Number(e.target.value))}><option value="3">Next 3 days</option><option value="7">Next 7 days</option><option value="14">Next 14 days</option></select>}>
 <div className="table-wrap"><table><thead><tr><th>Ingredient</th><th>Group</th><th>On hand</th><th>Committed</th><th>Available</th><th>Stock level</th><th>Update stock</th></tr></thead><tbody>{rows.map(r=><tr key={r.id}><td><b>{r.name}</b><span className="cell-sub">{r.id}</span></td><td>{r.group}</td><td>{fmtQty(r.onHand,r.unit)}</td><td>{fmtQty(r.committed,r.unit)}</td><td><b>{r.available<0?`short by ${fmtQty(-r.available,r.unit)}`:fmtQty(r.available,r.unit)}</b><span className="cell-sub">Reorder at {fmtQty(r.lowLevel,r.unit)}</span></td><td><span className={`stock-state ${String(r.level).toLowerCase()}`}>{({OK:'Fine',LOW:'Low',SHORT:'Short',OUT:'Empty'})[r.level]||r.level}</span><div className="stock-bar"><i style={{width:`${Math.max(2,Math.min(100,r.lowLevel>0?Math.max(0,r.available)/r.lowLevel*55:80))}%`}}/></div></td><td><AdjustCell row={r} adjust={adjust}/></td></tr>)}</tbody></table></div>
 </Panel>
 <div className="content-grid two-one"><Panel title="Shopping list" sub="Rounded up to whole packs.">{(stock?.shopping||[]).length?(stock.shopping.map(s=><div className="shopping-row" key={s.id}><div><b>{s.name}</b><span>{s.reason}. On the shelf {fmtQty(s.onHand,s.unit)}, booked orders need {fmtQty(s.committed,s.unit)}.</span></div><strong>Buy {fmtQty(s.buyAmount,s.unit)} ({s.packs} {s.packs===1?'pack':'packs'})</strong></div>)):<div className="inline-success"><CheckCircle2 size={16}/>No procurement shortfall in this window.</div>}</Panel><Panel title="Orders the stock cannot cover" sub="Orders affected by current availability">{(stock?.atRisk||[]).map(a=><div className="risk-row" key={a.orderId}><AlertTriangle size={15}/><div><b>{a.orderId} · {a.customerName}{a.dueAt?` · due ${fmtDate(a.dueAt)}`:''}</b><span>Missing: {(a.missing||[]).map(m=>`${m.name} (short by ${fmtQty(m.amount,m.unit)})`).join(', ')}</span></div></div>)}{!stock?.atRisk?.length&&<Empty title="No at-risk orders" text="All linked orders can be covered."/>}</Panel></div>
 <Panel title="Who gets the stock first?" sub="When stock is tight, orders are served most urgent first (the same urgency score as the ChefBoard). The usual way is first booked, first served."><div className="comparison-grid two"><div className="compare-card"><span>Urgency points kept covered: most urgent first</span><b>{stock?.urgentFirstServed??'—'}</b></div><div className="compare-card"><span>Urgency points kept covered: booking order</span><b>{stock?.bookingOrderServed??'—'}</b></div></div><p className="muted-note">Choosing the very best set of orders for many supplies at once is a hard problem, so this uses a simple greedy rule that is easy to explain and runs in one pass.</p><div className="setting-actions"><button className="btn secondary" onClick={resetStock}>Put the stock back to the starting amounts</button></div></Panel>
 </>
}
function Analytics({orders,orderMeta,plan,stock}){
 const made=orders.filter(o=>o.made).length,late=orders.filter(o=>o.late).length,deliveries=orders.filter(o=>o.fulfilment==='DELIVERY').length;
 const types=['CATERING','BAKERY','DAILY_FOOD'].map(t=>({name:t.replace('_',' '),count:orders.filter(o=>o.type===t).length}));
 const max=Math.max(1,...types.map(t=>t.count));
 return <><PageHead title="Analytics" sub="Numbers for the day you are looking at."/>
 <div className="metric-grid"><Metric label="Orders" value={orders.length} detail="on this day"/><Metric label="Made" value={made} detail={`${orders.length?Math.round(made/orders.length*100):0}% of orders`} icon={CheckCircle2} tone="teal"/><Metric label="Overdue" value={late} detail="Past due time" icon={Clock3} tone="red"/><Metric label="Delivery share" value={`${orders.length?Math.round(deliveries/orders.length*100):0}%`} detail={`${deliveries} delivery orders`} icon={Truck} tone="orange"/></div>
 <div className="content-grid two-one"><Panel title="Order mix" sub="Distribution by order category"><div className="bar-chart">{types.map((t,i)=><div className="bar-row" key={t.name}><span>{t.name}</span><div className="bar-track"><i style={{width:`${t.count/max*100}%`}} className={`bar-fill c${i}`}/></div><b>{t.count}</b></div>)}</div></Panel><Panel title="Delivery route" sub="For the day you are looking at"><div className="analytics-number"><strong>{Number(plan?.route?.totalKm||0).toFixed(2)}</strong><span>kilometres planned</span></div><div className="analytics-pair"><span>Stops</span><b>{plan?.route?.stops?.length||0}</b></div><div className="analytics-pair"><span>Pickups</span><b>{plan?.pickupCount||0}</b></div><div className="analytics-pair"><span>Within guarantee</span><b>{plan?.route?.withinGuarantee?'Yes':'—'}</b></div></Panel></div>
 <Panel title="How the checks are doing" sub="Real counts from the server."><div className="comparison-grid"><div className="compare-card"><span>Bloom filter hits</span><b>{orderMeta.bloomHits??'—'}</b></div><div className="compare-card"><span>Confirmed repeats blocked</span><b>{orderMeta.repeatsBlocked??'—'}</b></div><div className="compare-card"><span>Stock alerts</span><b>{(stock?.counts?.low||0)+(stock?.counts?.short||0)+(stock?.counts?.out||0)}</b></div><div className="compare-card"><span>Route stops</span><b>{plan?.route?.stops?.length||0}</b></div></div><p className="muted-note">Algorithm execution and duplicate screening remain in the Java backend. The frontend only visualises returned results.</p></Panel>
 </>
}
function Chips({options,value,onChange}){return <div className="chips">{options.map(([v,l])=><button type="button" key={v} className="chip-btn" aria-pressed={value===v} onClick={()=>onChange(v)}>{l}</button>)}</div>}
function SettingsPage({demoOpts,setDemoOpts,loadDemo,askClear,pretendNow,setPretendNow,date}){
 const set=(k,v)=>setDemoOpts(o=>({...o,[k]:v}));
 return <><PageHead title="Settings" sub="Sample orders and a pretend clock for trying things out."/>
 <div className="settings-grid"><Panel title="Try it with sample orders" sub="Makes sample orders so you can see how everything works. This replaces the orders you have now."><div className="setting-block"><b>How many orders?</b><Chips value={demoOpts.count} onChange={v=>set('count',v)} options={[[50,'50'],[100,'100'],[300,'300'],[1000,'1000']]}/></div><div className="setting-block"><b>Spread over how many days?</b><Chips value={demoOpts.days} onChange={v=>set('days',v)} options={[[1,'1 day'],[3,'3 days'],[7,'7 days']]}/></div><div className="setting-block"><b>How many accidental repeats?</b><Chips value={demoOpts.dup} onChange={v=>set('dup',v)} options={[[0,'None'],[10,'A few'],[25,'Many']]}/></div><div className="setting-actions"><button className="btn primary" onClick={loadDemo}><FlaskConical size={16}/>Load sample orders</button><button className="btn danger" onClick={askClear}><Trash2 size={16}/>Delete all orders</button></div></Panel>
 <Panel title="Pretend it is a different time" sub="Handy to show how urgency changes during the day. It only changes what you see here."><div className="setting-block"><b>On the day you are looking at ({date}), pretend it is:</b><Chips value={pretendNow} onChange={setPretendNow} options={[[`${date}T08:00`,'8 in the morning'],[`${date}T12:00`,'12 noon'],[`${date}T17:00`,'5 in the evening']]}/>{pretendNow&&<button className="btn secondary" onClick={()=>setPretendNow('')}><Clock3 size={15}/>Use the real time</button>}</div></Panel>
 <Panel title="System status" sub="Connection and runtime details."><div className="system-row"><span>Server</span><span className="live-tag"><i/>Connected when available</span></div><div className="system-row"><span>Algorithm execution</span><b>Java server-side</b></div><div className="system-row"><span>Persistence</span><b>In-memory for this run</b></div><div className="system-row"><span>Frontend</span><b>React + Vite</b></div></Panel></div>
 </>
}
function Empty({title,text,action}){return <div className="empty"><b>{title}</b><span>{text}</span>{action}</div>}
function StockCheck({family,quantity,pretendNow}){
 const [res,setRes]=useState(null),[err,setErr]=useState('');
 useEffect(()=>{
  setRes(null);setErr('');
  const q=Number(quantity);
  if(!family||!(q>0))return;
  let stale=false;
  const timer=setTimeout(async()=>{
   try{const r=await request(`/orders/check${pretendNow?`?now=${encodeURIComponent(pretendNow)}`:''}`,'POST',{family,quantity:q});if(!stale)setRes(r)}
   catch(e){if(!stale)setErr(e.message)}
  },300);
  return()=>{stale=true;clearTimeout(timer)};
 },[family,quantity,pretendNow]);
 if(err)return <div className="check-box bad"><div className="check-title"><AlertTriangle size={15}/>{err}</div></div>;
 if(!res)return null;
 return <div className={`check-box ${res.possible?'ok':'bad'}`}><div className="check-title">{res.possible?<CheckCircle2 size={15}/>:<AlertTriangle size={15}/>}{res.message}</div>
  {res.unitsStillPossible>=0&&res.limitedBy&&<p>With what is free now, about {res.unitsStillPossible} more {res.unit} of {String(res.family).toLowerCase()} could still be made. Running out first: {res.limitedBy}.</p>}
  <ul>{(res.lines||[]).map(l=><li key={l.name} className={l.availableAfter<0?'short':''}>{l.name}: needs {fmtQty(l.use,l.unit)}, {l.availableAfter<0?`short by ${fmtQty(-l.availableAfter,l.unit)}`:`${fmtQty(l.availableAfter,l.unit)} left after`}</li>)}</ul></div>
}
function ConfirmClear({onCancel,onOk}){
 return <motion.div className="overlay center" initial={{opacity:0}} animate={{opacity:1}} exit={{opacity:0}} onMouseDown={e=>e.target===e.currentTarget&&onCancel()}><motion.div className="confirm-card" initial={{scale:.96,opacity:0}} animate={{scale:1,opacity:1}} exit={{scale:.96,opacity:0}}><h3>Delete all orders?</h3><p>This removes every saved order. It cannot be undone.</p><div className="confirm-actions"><button className="btn secondary" onClick={onCancel}>Keep them</button><button className="btn danger" onClick={onOk}>Yes, delete all</button></div></motion.div></motion.div>
}
function OrderModal({form,setForm,localities,families,pretendNow,onClose,onSubmit,saving}){
 const change=(k,v)=>setForm(f=>({...f,[k]:v}));
 const fams=families.filter(f=>!f.type||f.type===form.type);
 const pickType=v=>setForm(f=>({...f,type:v,family:'',quantity:1}));
 const pickFamily=id=>{const fam=families.find(x=>x.id===id);setForm(f=>({...f,family:id,quantity:fam?fam.minQty:1}))};
 return <motion.div className="overlay" initial={{opacity:0}} animate={{opacity:1}} exit={{opacity:0}} onMouseDown={e=>e.target===e.currentTarget&&onClose()}><motion.form className="drawer modal" initial={{x:40,opacity:0}} animate={{x:0,opacity:1}} exit={{x:35,opacity:0}} onSubmit={onSubmit}><div className="drawer-head"><div><h2>New order</h2><p>We check for repeat orders before saving.</p></div><button type="button" className="icon-btn" onClick={onClose}><X size={19}/></button></div><div className="form-body"><label>Customer name<input required value={form.customerName} onChange={e=>change('customerName',e.target.value)} placeholder="e.g. Aanya Shah"/></label><div className="form-two"><label>Phone<input required value={form.phone} onChange={e=>change('phone',e.target.value)} placeholder="10-digit number"/></label><label>Item / order description<input required value={form.item} onChange={e=>change('item',e.target.value)} placeholder="e.g. Family thali"/></label></div><div className="form-two"><label>Order category<select value={form.type} onChange={e=>pickType(e.target.value)}><option value="DAILY_FOOD">Daily food</option><option value="BAKERY">Bakery</option><option value="CATERING">Catering</option></select></label><label>Fulfilment<select value={form.fulfilment} onChange={e=>change('fulfilment',e.target.value)}><option value="DELIVERY">Delivery</option><option value="PICKUP">Pickup</option></select></label></div>{form.fulfilment==='DELIVERY'&&<label>Delivery locality<select required value={form.locality} onChange={e=>change('locality',e.target.value)}><option value="">Select locality</option>{localities.map(l=><option key={l.name} value={l.name}>{l.name}</option>)}</select></label>}<label>Due date and time<input required type="datetime-local" value={form.dueAt} onChange={e=>change('dueAt',e.target.value)}/></label><div className="form-divider">Check the kitchen stock (optional)</div><label>Recipe / product family<select value={form.family} onChange={e=>pickFamily(e.target.value)}><option value="">No stock-linked product</option>{fams.map(f=><option value={f.id} key={f.id}>{f.label} ({f.unit})</option>)}</select></label>{form.family&&<label>Quantity<input type="number" min="0.1" step="0.1" value={form.quantity} onChange={e=>change('quantity',e.target.value)}/></label>}{form.family&&<StockCheck family={form.family} quantity={form.quantity} pretendNow={pretendNow}/>}</div><div className="drawer-foot"><button type="button" className="btn secondary" onClick={onClose}>Cancel</button><button className="btn primary" disabled={saving}>{saving?'Saving…':<><Plus size={17}/>Save order</>}</button></div></motion.form></motion.div>
}
function OrderDrawer({order,eta,onClose,onMade}){
 return <motion.div className="overlay" initial={{opacity:0}} animate={{opacity:1}} exit={{opacity:0}} onMouseDown={e=>e.target===e.currentTarget&&onClose()}><motion.aside className="drawer detail-drawer" initial={{x:460}} animate={{x:0}} exit={{x:460}} transition={{duration:.22}}><div className="drawer-head"><div><h2>{order.item}</h2><p>Order {order.id}</p></div><button className="icon-btn" onClick={onClose}><X size={19}/></button></div><div className="detail-content"><div className="detail-status"><Status late={order.late} made={order.made}/><span className="priority high">Urgency {Number(order.score||0).toFixed(1)}</span></div><div className="detail-person"><div className="avatar large">{(order.customerName||'?').slice(0,1)}</div><div><h3>{order.customerName}</h3><span>{order.phone}</span></div></div>{[['Order item',order.item],['Category',order.type?.replace('_',' ')],['Fulfilment',order.fulfilment],['Locality',order.locality||'Kitchen pickup'],['Due date',fmtDate(order.dueAt)],['Time remaining',order.minutesLeft<0?`${Math.abs(order.minutesLeft)} minutes overdue`:`${order.minutesLeft} minutes`],['Stock-linked product',order.family||'Not linked'],['Quantity',order.quantity!=null?`${fmtNum(order.quantity)} ${order.familyUnit||''}`:'—']].map(([k,v])=><div className="detail-field" key={k}><span>{k}</span><b>{v}</b></div>)}<div className="wa-box"><b>WhatsApp message to the customer</b><p>{waMessage(order,eta)}</p><WaButton order={order} eta={eta}/></div><div className="detail-note"><Activity size={16}/><span>The urgency score is worked out by the server. A higher score means do it sooner.</span></div></div><div className="drawer-foot"><button className="btn secondary" onClick={onClose}>Close</button>{order.family&&!order.made&&<button className="btn primary" onClick={()=>onMade(order.id)}><Check size={16}/>Mark as made</button>}</div></motion.aside></motion.div>
}
createRoot(document.getElementById('root')).render(<App/>);
