const API_BASE = 'http://localhost:8080/api';

const ABI = [
  'zero','ra','sp','gp','tp',
  't0','t1','t2','s0','s1',
  'a0','a1','a2','a3','a4','a5','a6','a7',
  's2','s3','s4','s5','s6','s7','s8','s9','s10','s11',
  't3','t4','t5','t6'
];

const EXAMPLES = {
  basic_arithmetic:
`# Basic Arithmetic
        addi x1, x0, 15
        addi x2, x0, 7
        add  x3, x1, x2
        sub  x4, x1, x2
        and  x5, x1, x2
        or   x6, x1, x2
        xor  x7, x1, x2
        slt  x8, x2, x1
        sll  x9, x2, x2
        srl  x10, x9, x2
`,
  immediate_ops:
`# Immediate Operations
        addi  x1, x0, -1
        addi  x2, x0, 255
        andi  x3, x1, x2
        ori   x4, x0, 0x0F
        xori  x5, x1, 0xFF
        slti  x6, x1, 0
        sltiu x7, x1, 1
        slli  x8, x2, 4
        srli  x9, x8, 4
        srai  x10, x1, 1
`,
  memory_ops:
`# Memory Load & Store
        addi  x1, x0, 42
        addi  x2, x0, 0
        sw    x1, 0(x2)
        lw    x3, 0(x2)
        addi  x4, x0, -128
        sb    x4, 4(x2)
        lb    x5, 4(x2)
        lbu   x6, 4(x2)
        addi  x7, x0, 1000
        sh    x7, 8(x2)
        lh    x8, 8(x2)
        lhu   x9, 8(x2)
`,
  branching_loop:
`# Sum 1+2+3+4+5 = 15
        addi  x1, x0, 1   # counter
        addi  x2, x0, 6   # limit+1
        addi  x3, x0, 0   # sum

loop:   bge   x1, x2, done
        add   x3, x3, x1
        addi  x1, x1, 1
        beq   x0, x0, loop

done:   addi  x4, x0, 99
`,
  mixed_program:
`# Fibonacci (stores F0..F5 in memory)
        addi  x1, x0, 0   # a
        addi  x2, x0, 1   # b
        addi  x3, x0, 6   # count
        addi  x4, x0, 0   # i
        addi  x5, x0, 0   # addr

fib:    bge   x4, x3, done
        sw    x1, 0(x5)
        add   x6, x1, x2
        addi  x1, x2, 0
        addi  x2, x6, 0
        addi  x4, x4, 1
        addi  x5, x5, 4
        beq   x0, x0, fib

done:   addi  x7, x0, 42
`
};

let simResult   = null;
let currentStep = 0;
let prevRegs    = null; // registers from previous step — used to highlight writes

const editor       = document.getElementById('editor');
const gutter       = document.getElementById('gutter');
const exampleSel   = document.getElementById('exampleSelect');
const parseBtn     = document.getElementById('parseBtn');
const assembleBtn  = document.getElementById('assembleBtn');
const resetBtn     = document.getElementById('resetBtn');
const tabEditor    = document.getElementById('tabEditor');
const tabSimulator = document.getElementById('tabSimulator');
const viewEditor   = document.getElementById('view-editor');
const viewSim      = document.getElementById('view-simulator');
const decodeOutput = document.getElementById('decode-output');
const traceScroll  = document.getElementById('traceScroll');
const traceEmpty   = document.getElementById('traceEmpty');
const regBody      = document.getElementById('regBody');
const memBody      = document.getElementById('memBody');
const memTable     = document.getElementById('memTable');
const memEmpty     = document.getElementById('memEmpty');
const prevBtn      = document.getElementById('prevBtn');
const nextBtn      = document.getElementById('nextBtn');
const stepInfo     = document.getElementById('stepInfo');
const statusText   = document.getElementById('statusText');

function init() {
  editor.value = EXAMPLES.basic_arithmetic;
  buildRegTable(new Array(32).fill(0));
  updateGutter();
  setStatus('Ready', '');
}

function switchTab(t) {
  const isEditor = t === 'editor';
  tabEditor.classList.toggle('active', isEditor);
  tabSimulator.classList.toggle('active', !isEditor);
  viewEditor.classList.toggle('active', isEditor);
  viewSim.classList.toggle('active', !isEditor);
}

tabEditor.addEventListener('click',    () => switchTab('editor'));
tabSimulator.addEventListener('click', () => switchTab('simulator'));

function updateGutter() {
  const n = editor.value.split('\n').length;
  gutter.textContent = Array.from({length: n}, (_, i) => i + 1).join('\n');
}
editor.addEventListener('input',  updateGutter);
editor.addEventListener('scroll', () => { gutter.scrollTop = editor.scrollTop; });

async function parseOnly() {
  const code = editor.value.trim();
  if (!code) return;
  setStatus('Parsing...', '');
  try {
    const res  = await fetch(`${API_BASE}/validate`, {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({code})});
    const data = await res.json();
    if (!data.valid) {
      decodeOutput.innerHTML = `<p class="placeholder" style="color:#f44747">Error: ${esc(data.errorMessage)}</p>`;
      setStatus('Parse error', 'error');
    } else {
      renderDecode(data.instructions);
      setStatus(`Parsed ${data.instructionCount} instruction(s)`, 'ok');
    }
  } catch {
    setStatus('Cannot reach backend (port 8080)', 'error');
  }
}

function renderDecode(instrs) {
  const frag = document.createDocumentFragment();
  instrs.forEach((ins, i) => {
    const row = document.createElement('div');
    row.className = 'decode-row';
    row.innerHTML =
      `<span class="d-n">${i}</span>` +
      `<span class="d-mn">${esc(ins.mnemonic)}</span>` +
      `<span class="d-args">${esc(ins.raw)}</span>` +
      `<span class="d-meta">fmt=${ins.format}  rd=x${ins.rd}  rs1=x${ins.rs1}  rs2=x${ins.rs2}  imm=${ins.imm}</span>`;
    frag.appendChild(row);
  });
  decodeOutput.innerHTML = '';
  decodeOutput.appendChild(frag);
}

async function assembleAndRun() {
  const code = editor.value.trim();
  if (!code) return;
  setStatus('Running...', '');
  navEnable(false);
  try {
    const res  = await fetch(`${API_BASE}/simulate`, {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({code})});
    const data = await res.json();
    simResult  = data;
    if (!data.success && !data.steps?.length) {
      renderTrace([]);
      setStatus(data.errorMessage || 'Simulation failed', 'error');
      return;
    }
    renderTrace(data.steps);
    jumpToStep(0);
    setStatus(
      data.success ? `Done — ${data.totalSteps} steps` : `Halted: ${data.errorMessage}`,
      data.success ? 'ok' : 'error'
    );
    switchTab('simulator');
  } catch {
    setStatus('Cannot reach backend (port 8080)', 'error');
  }
}

function resetSim() {
  simResult = null; currentStep = 0; prevRegs = null;
  traceScroll.innerHTML = '';
  traceScroll.appendChild(traceEmpty);
  traceEmpty.textContent = 'No simulation loaded.';
  buildRegTable(new Array(32).fill(0));
  memBody.innerHTML = '';
  memTable.style.display = 'none';
  memEmpty.style.display = '';
  navEnable(false);
  stepInfo.textContent = '—';
  setStatus('Reset', '');
}

function renderTrace(steps) {
  traceScroll.innerHTML = '';
  if (!steps?.length) {
    traceScroll.appendChild(traceEmpty);
    traceEmpty.textContent = 'No steps to display.';
    navEnable(false);
    return;
  }
  const frag = document.createDocumentFragment();
  steps.forEach((s, i) => {
    const row = document.createElement('div');
    row.className   = 'trace-row';
    row.dataset.idx = i;
    if (s.branchTaken === true)  row.classList.add('branch-taken');
    if (s.branchTaken === false) row.classList.add('branch-not-taken');
    if (s.error)                 row.classList.add('has-error');
    row.innerHTML =
      `<span class="tr-num">#${s.stepNumber}</span>` +
      `<span class="tr-pc">0x${s.pc.toString(16).padStart(4,'0').toUpperCase()}</span>` +
      `<span class="tr-body">${esc(s.instruction || '')}</span>` +
      `<span class="tr-desc">${s.error ? '⚠ ' + esc(s.error) : esc(s.description)}</span>`;
    row.addEventListener('click', () => jumpToStep(i));
    frag.appendChild(row);
  });
  traceScroll.appendChild(frag);
  navEnable(true);
}

function jumpToStep(idx) {
  if (!simResult?.steps?.length) return;
  idx = Math.max(0, Math.min(idx, simResult.steps.length - 1));
  const prevIdx  = currentStep;
  currentStep    = idx;
  const step     = simResult.steps[idx];
  const prevStep = prevIdx > 0 ? simResult.steps[prevIdx - 1] : null;

  document.querySelectorAll('.trace-row').forEach(r => r.classList.remove('active'));
  const active = document.querySelector(`.trace-row[data-idx="${idx}"]`);
  if (active) { active.classList.add('active'); active.scrollIntoView({block:'nearest'}); }

  prevRegs = prevStep ? prevStep.registers : new Array(32).fill(0);
  buildRegTable(step.registers);
  renderMemory(step.memory);
  stepInfo.textContent = `Step ${idx + 1} / ${simResult.steps.length}`;
  prevBtn.disabled     = idx === 0;
  nextBtn.disabled     = idx === simResult.steps.length - 1;
}

function buildRegTable(regs) {
  regBody.innerHTML = '';
  const frag = document.createDocumentFragment();
  for (let i = 0; i < 32; i++) {
    const val     = regs ? regs[i] : 0;
    const changed = prevRegs && i !== 0 && prevRegs[i] !== val; // skip x0 — hardwired zero
    const tr      = document.createElement('tr');
    if (changed) tr.classList.add('reg-changed');
    tr.innerHTML =
      `<td>x${i}</td>` +
      `<td>${ABI[i]}</td>` +
      `<td>${val}</td>` +
      `<td>0x${(val >>> 0).toString(16).padStart(8,'0')}</td>`;
    frag.appendChild(tr);
  }
  regBody.appendChild(frag);
}

function renderMemory(memMap) {
  memBody.innerHTML = '';
  if (!memMap || !Object.keys(memMap).length) {
    memTable.style.display = 'none';
    memEmpty.style.display = '';
    return;
  }
  memEmpty.style.display = 'none';
  memTable.style.display = '';
  const frag = document.createDocumentFragment();
  Object.keys(memMap).map(Number).sort((a,b)=>a-b).forEach(addr => {
    const val = memMap[addr];
    const tr  = document.createElement('tr');
    tr.innerHTML =
      `<td>0x${addr.toString(16).padStart(4,'0')}</td>` +
      `<td>${val}</td>` +
      `<td>0x${(val>>>0).toString(16).padStart(8,'0')}</td>`;
    frag.appendChild(tr);
  });
  memBody.appendChild(frag);
}

function navEnable(on) {
  prevBtn.disabled  = !on;
  nextBtn.disabled  = !on;
}

function setStatus(msg, type) {
  statusText.textContent = msg;
  statusText.className   = type || '';
}

function esc(s) {
  if (s == null) return '';
  return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}

parseBtn.addEventListener('click',    parseOnly);
assembleBtn.addEventListener('click', assembleAndRun);
resetBtn.addEventListener('click',    resetSim);
prevBtn.addEventListener('click',     () => jumpToStep(currentStep - 1));
nextBtn.addEventListener('click',     () => jumpToStep(currentStep + 1));

exampleSel.addEventListener('change', () => {
  const v = exampleSel.value;
  if (v && EXAMPLES[v]) {
    editor.value     = EXAMPLES[v];
    exampleSel.value = '';
    updateGutter();
    resetSim();
    setStatus('Example loaded', '');
  }
});

document.addEventListener('keydown', e => {
  if (e.ctrlKey && e.key === 'Enter') { e.preventDefault(); assembleAndRun(); }
  if (document.activeElement !== editor) {
    if (e.key === 'ArrowRight') jumpToStep(currentStep + 1);
    if (e.key === 'ArrowLeft')  jumpToStep(currentStep - 1);
  }
});

init();
