const $=(s,r=document)=>r.querySelector(s), $$=(s,r=document)=>[...r.querySelectorAll(s)];
const money=v=>Number(v||0).toLocaleString('pt-BR',{style:'currency',currency:'BRL'});
const num=v=>Number(String(v??0).replace(',','.'))||0;
const uid=()=>crypto.randomUUID?.()||Date.now()+Math.random().toString(16).slice(2);
const form=$('#quoteForm'); let items=[]; let deferredPrompt=null; let saveTimer;

const templates={
  CFTV:q=>[
    ['CFTV',`Câmera ${q.tech}`,'un',q.cameras],['CFTV',q.tech==='IP'?'NVR compatível':'DVR compatível','un',q.cameras?1:0],['CFTV','HD para vigilância','un',q.cameras?1:0],['CFTV',q.tech==='IP'?'Switch PoE dimensionado':'Fonte de alimentação dimensionada','un',q.cameras?1:0],['CFTV','Suporte de câmera','un',q.cameras]
  ],
  'Controle de acesso':q=>[
    ['Controle de acesso','Leitor facial / RFID de entrada','un',q.doors],['Controle de acesso','Botoeira de saída','un',q.doors],['Controle de acesso','Fechadura elétrica / eletroímã','un',q.doors],['Controle de acesso','Mola aérea','un',q.doors],['Controle de acesso','Módulo fonte carregadora','un',q.doors],['Controle de acesso','Bateria de contingência','un',q.doors]
  ],
  Alarme:q=>[
    ['Alarme',`Central de alarme ${q.alarm.toLowerCase()}`,'un',q.sensors?1:0],['Alarme',`Sensor ${q.alarm.toLowerCase()}`,'un',q.sensors],['Alarme','Sirene','un',q.sensors?1:0],['Alarme','Bateria 12 V 7 Ah','un',q.sensors?1:0],['Alarme','Controle remoto adicional','un',q.sensors?2:0]
  ],
  'Cerca elétrica':q=>[
    ['Cerca elétrica','Eletrificador dimensionado','un',q.fence?1:0],['Cerca elétrica','Haste para cerca elétrica','un',Math.ceil(q.fence/3)],['Cerca elétrica','Fio inox para seis vias','m',Math.ceil(q.fence*6*q.loss)],['Cerca elétrica','Cabo de alta isolação','m',Math.max(0,Math.ceil(q.fence/10)*5)],['Cerca elétrica','Conjunto de aterramento','cj',q.fence?1:0],['Cerca elétrica','Bateria 12 V 7 Ah','un',q.fence?1:0],['Cerca elétrica','Placa de identificação local protegido','un',Math.ceil(q.fence/10)],['Cerca elétrica','Isolador com parafuso','un',Math.ceil(q.fence/3)*6]
  ]
};

function infraRows(system,q){
  const points=system==='CFTV'?q.cameras:system==='Controle de acesso'?q.doors:system==='Alarme'?q.sensors:q.fence?1:0;
  if(!points)return[];
  const routeBase=system==='Cerca elétrica'?q.distance:points*q.distance;
  const routeMeters=q.routeValue===1?0:Math.ceil(routeBase*q.loss);
  const cableBase=system==='Alarme'&&q.alarm==='Sem fio'?10:routeBase;
  const cableMeters=Math.ceil(cableBase*q.loss);
  const prefix=`${system}: `;
  const routeName=q.routeValue===1.15?'Canaleta com fita dupla face':q.routeValue===1.25?'Eletroduto/conduíte':q.routeValue===1.45?'Eletromangueira corrugada para trecho enterrado':'';
  const spacing=q.routeValue===1.15?2:.5;
  const fixQty=routeMeters?Math.ceil(routeMeters/spacing):0;
  const rows=[];
  if(system==='CFTV')rows.push(['Infraestrutura',prefix+(q.tech==='IP'?'Cabo de rede 100% cobre':'Cabo para CFTV'),'m',cableMeters],['Infraestrutura',prefix+(q.tech==='IP'?'Conector RJ45':'Balun passivo'),'un',points*2]);
  if(system==='Controle de acesso')rows.push(['Infraestrutura',prefix+'Cabo para controle de acesso','m',cableMeters],['Infraestrutura',prefix+'Terminais e conectores','un',points*4]);
  if(system==='Alarme')rows.push(['Infraestrutura',prefix+(q.alarm==='Sem fio'?'Cabo para central e sirene':'Cabo de alarme'),'m',cableMeters],['Infraestrutura',prefix+'Terminais e conectores','un',Math.max(4,points*2)]);
  if(system==='Cerca elétrica')rows.push(['Infraestrutura',prefix+'Cabo elétrico de alimentação','m',cableMeters]);
  rows.push(['Infraestrutura',prefix+'Caixa de passagem/proteção','un',Math.max(1,points)]);
  if(routeMeters)rows.push(['Infraestrutura',prefix+routeName,'m',routeMeters],['Infraestrutura',prefix+'Curvas','un',Math.ceil(routeMeters/6)],['Infraestrutura',prefix+'Luvas/emendas','un',Math.ceil(routeMeters/3)],['Infraestrutura',prefix+'Abraçadeiras/fixadores','un',fixQty],['Infraestrutura',prefix+'Buchas','un',fixQty],['Infraestrutura',prefix+'Parafusos','un',fixQty]);
  return rows;
}

function values(){const f=new FormData(form),o={};for(const [k,v] of f)o[k]=v;$$('input[type=checkbox]',form).forEach(x=>o[x.name]=x.checked);return o}
function setValues(o){Object.entries(o||{}).forEach(([k,v])=>{const e=form.elements[k];if(!e)return;if(e.type==='checkbox')e.checked=!!v;else e.value=v})}
function item(system,name,unit,qty,cost=0){return{id:uid(),system,name,unit,qty,cost}}
function dimension(){const v=values(),q={cameras:num(v.cameraQty),doors:num(v.doorQty),sensors:num(v.sensorQty),fence:num(v.fenceMeters),distance:Math.max(1,num(v.distance)),tech:v.cameraTech,alarm:v.alarmType,routeValue:num(v.route),loss:1+num(v.lossPercent)/100};const chosen=[];if(v.sys_cftv)chosen.push('CFTV');if(v.sys_access)chosen.push('Controle de acesso');if(v.sys_alarm)chosen.push('Alarme');if(v.sys_fence)chosen.push('Cerca elétrica');if(!chosen.length)return alertBox('Escolha as soluções','Marque pelo menos uma linha antes de dimensionar.');const oldCosts=new Map(items.map(x=>[`${x.system}|${x.name}`,x.cost]));const rows=chosen.flatMap(s=>[...templates[s](q),...infraRows(s,q)]);items=rows.filter(x=>num(x[3])>0).map(x=>item(x[0],x[1],x[2],x[3],oldCosts.get(`${x[0]}|${x[1]}`)||0));renderItems();calculate();scheduleSave()}
function addItem(data=item('Infraestrutura','Novo item','un',1,0)){items.push(data);renderItems();calculate()}
function renderItems(){const body=$('#itemBody');body.innerHTML='';items.forEach((it,i)=>{const row=$('#itemTemplate').content.firstElementChild.cloneNode(true);row.dataset.id=it.id;$$('[data-k]',row).forEach(el=>{const k=el.dataset.k;el.value=it[k]??'';el.oninput=()=>{it[k]=['qty','cost'].includes(k)?num(el.value):el.value;calculate();scheduleSave()}});row.querySelector('.remove').onclick=()=>{items.splice(i,1);renderItems();calculate();scheduleSave()};body.append(row)})}
function complexity(){const v=values(),factors=[num(v.height),num(v.ceiling),num(v.roof),num(v.route),num(v.operation)],max=Math.max(...factors),extra=factors.filter(x=>x!==max).reduce((a,x)=>a+(x-1)*.35,0);return Math.max(1,max+extra)}
function baseHours(v){let h=0;if(v.sys_cftv)h+=num(v.cameraQty)*1.5+3;if(v.sys_access)h+=num(v.doorQty)*5;if(v.sys_alarm)h+=4+num(v.sensorQty)*(v.alarmType==='Sem fio'?.7:v.alarmType==='Híbrido'?1.1:1.5);if(v.sys_fence)h+=num(v.fenceMeters)*.18+4;return h}
function saleUnit(it,v){const margin=num(v.materialMargin)/100,tax=num(v.tax)/100;return it.cost*(1+margin)/(1-Math.min(tax,.8))}
function calculate(){const v=values(),factor=complexity();$('#complexityFactor').textContent=factor.toLocaleString('pt-BR',{minimumFractionDigits:2,maximumFractionDigits:2})+'×';let materialCost=0,materialSale=0,missing=0;$$('#itemBody tr').forEach((row,i)=>{const it=items[i],sale=saleUnit(it,v),total=sale*num(it.qty);materialCost+=num(it.cost)*num(it.qty);materialSale+=total;if(!num(it.cost))missing++;row.querySelector('[data-sale]').textContent=money(sale);row.querySelector('[data-total]').textContent=money(total)});const hours=baseHours(v)*factor,dayHours=Math.max(1,num(v.dayHours)),days=Math.max(num(v.estimatedDays),hours/dayHours),crewDay=num(v.leadQty)*num(v.leadDay)+num(v.techQty)*num(v.techDay)+num(v.helperQty)*num(v.helperDay),laborCost=days*(crewDay+num(v.food)+num(v.fuel)),laborSale=laborCost*(1+num(v.laborMargin)/100),subtotal=materialSale+laborSale+num(v.freight)-num(v.discount),total=Math.max(0,subtotal);$('#materialCost').textContent=money(materialCost);$('#materialSale').textContent=money(materialSale);$('#laborHours').textContent=hours.toLocaleString('pt-BR',{maximumFractionDigits:1})+' h';$('#laborSale').textContent=money(laborSale);$('#grandTotal').textContent=money(total);const warn=$('#missingPrices');warn.classList.toggle('hidden',missing===0);warn.textContent=missing?`${missing} item(ns) ainda sem custo de compra. O total ficará incompleto até preencher os preços.`:'';renderProposal(v,total,factor,days)}
function renderProposal(v,total,factor,days){const systems=[];if(v.sys_cftv)systems.push('CFTV');if(v.sys_access)systems.push('Controle de acesso');if(v.sys_alarm)systems.push('Alarme');if(v.sys_fence)systems.push('Cerca elétrica');$('#proposalClient').innerHTML=`<div><b>Cliente</b><br>${v.client||'Não informado'}</div><div><b>Local</b><br>${v.address||v.propertyType}</div><div><b>Data</b><br>${v.date?new Date(v.date+'T12:00').toLocaleDateString('pt-BR'):'--'}</div><div><b>Objetivo</b><br>${v.objective||'Projeto de segurança eletrônica'}</div>`;$('#proposalScope').innerHTML=systems.map(s=>`<span class="scope-tag">${s}</span>`).join('');const groups=Object.groupBy?Object.groupBy(items,x=>x.system):items.reduce((a,x)=>((a[x.system]??=[]).push(x),a),{});$('#proposalItems').innerHTML=Object.entries(groups).map(([g,arr])=>`<div class="proposal-section"><h3>${g}</h3>${arr.map(x=>`<div class="proposal-line"><span>${x.name}</span><b>${num(x.qty).toLocaleString('pt-BR')} ${x.unit}</b></div>`).join('')}</div>`).join('');$('#proposalTerms').textContent=`Prazo estimado: ${Math.ceil(days)} dia(s) · Validade: ${v.validity||10} dias`;const labels={height:$('[name=height]').selectedOptions[0].text,ceiling:$('[name=ceiling]').selectedOptions[0].text,roof:$('[name=roof]').selectedOptions[0].text,route:$('[name=route]').selectedOptions[0].text,operation:$('[name=operation]').selectedOptions[0].text};$('#proposalComplexity').textContent=`Instalação considerada: ${labels.height}; ${labels.ceiling}; ${labels.roof}; ${labels.route}; ${labels.operation}. Fator técnico interno ${factor.toFixed(2)}. ${v.difficultyNotes||''}`}
function state(){return{version:1,form:values(),items,updatedAt:new Date().toISOString()}}
function save(){localStorage.setItem('rovan-orcamentos',JSON.stringify(state()));$('#saveState').textContent='Salvo agora'}
function scheduleSave(){clearTimeout(saveTimer);$('#saveState').textContent='Salvando…';saveTimer=setTimeout(save,500)}
function load(){const raw=localStorage.getItem('rovan-orcamentos');if(raw){try{const s=JSON.parse(raw);setValues(s.form);items=s.items||[]}catch{}}if(!form.elements.date.value)form.elements.date.value=new Date().toISOString().slice(0,10);renderItems();calculate()}
function reset(){if(!confirm('Iniciar um novo orçamento e limpar os dados atuais?'))return;form.reset();form.elements.date.value=new Date().toISOString().slice(0,10);form.elements.validity.value=10;form.elements.leadDay.value=400;form.elements.techDay.value=400;form.elements.helperDay.value=150;form.elements.dayHours.value=8;form.elements.fuel.value=100;form.elements.food.value=80;form.elements.materialMargin.value=30;form.elements.laborMargin.value=30;form.elements.estimatedDays.value=1;items=[];renderItems();calculate();save()}
function backup(){const a=document.createElement('a');a.href=URL.createObjectURL(new Blob([JSON.stringify(state(),null,2)],{type:'application/json'}));a.download=`ROVAN_Orcamento_${form.elements.client.value||'novo'}.json`;a.click();URL.revokeObjectURL(a.href)}
async function importFile(file){try{const s=JSON.parse(await file.text());setValues(s.form);items=s.items||[];renderItems();calculate();save();alertBox('Backup importado','O orçamento foi carregado neste aparelho.')}catch{alertBox('Arquivo inválido','Não foi possível importar este orçamento.')}}
function alertBox(title,text){$('#dialogTitle').textContent=title;$('#dialogText').textContent=text;$('#dialog').showModal()}
function installHelp(){const ua=navigator.userAgent;if(/iPhone|iPad|iPod/i.test(ua))return alertBox('Instalar no iPhone','Abra no Safari, toque em Compartilhar e em Adicionar à Tela de Início.');if(/Android/i.test(ua))return alertBox('Instalar no Android','Abra no Chrome, toque no menu ⋮ e escolha Instalar app ou Adicionar à tela inicial.');alertBox('Instalar aplicativo','No Chrome ou Edge, use o ícone de instalação na barra de endereço ou o menu Instalar aplicativo.')}

form.addEventListener('input',()=>{calculate();scheduleSave()});$('#dimensionBtn').onclick=dimension;$('#addItemBtn').onclick=()=>addItem();$('#saveBtn').onclick=save;$('#newBtn').onclick=reset;$('#exportBtn').onclick=backup;$('#importInput').onchange=e=>{if(e.target.files[0])importFile(e.target.files[0]);e.target.value=''};$('#printBtn').onclick=()=>window.print();$('#dialogClose').onclick=()=>$('#dialog').close();window.addEventListener('beforeinstallprompt',e=>{e.preventDefault();deferredPrompt=e});$('#installBtn').onclick=async()=>{if(!deferredPrompt)return installHelp();try{deferredPrompt.prompt();await deferredPrompt.userChoice}catch{installHelp()}finally{deferredPrompt=null}};
if('serviceWorker'in navigator)addEventListener('load',()=>navigator.serviceWorker.register('sw.js'));
load();
