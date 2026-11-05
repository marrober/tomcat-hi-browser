const SEVERITIES = [
  { key: "critical", label: "Critical", color: "#7d1007" },
  { key: "high", label: "High", color: "#c9190b" },
  { key: "medium", label: "Medium", color: "#f0ab00" },
  { key: "low", label: "Low", color: "#0066cc" },
  { key: "negligible", label: "Negligible", color: "#8bc1f7" },
  { key: "unknown", label: "Unknown", color: "#6a6e73" }
];

const statusNode = document.querySelector("#status");
const errorNode = document.querySelector("#error");
const chartNode = document.querySelector("#chart");
const tableBody = document.querySelector("#report-body");
const refreshButton = document.querySelector("#refresh");

let pollTimer = null;
let loadingAttempts = 0;
let requestInFlight = false;

refreshButton.addEventListener("click", () => load(true));

load(false);

async function load(force) {
  if (requestInFlight) {
    return;
  }
  requestInFlight = true;
  refreshButton.disabled = true;
  try {
    const response = await fetch("api/vulnerabilities", {
      method: force ? "POST" : "GET",
      headers: { Accept: "application/json" },
      cache: "no-store"
    });
    if (!response.ok) {
      throw new Error("The vulnerability report request failed with HTTP " + response.status);
    }
    const data = await response.json();
    render(data);
    schedule(data.refreshMinutes || 60);
    if (data.status === "loading") {
      loadingAttempts += 1;
      if (loadingAttempts < 30) {
        window.setTimeout(() => load(false), 2000);
      }
    } else {
      loadingAttempts = 0;
    }
  } catch (error) {
    errorNode.hidden = false;
    errorNode.textContent = error.message;
    statusNode.textContent = "The chart could not be updated.";
  } finally {
    requestInFlight = false;
    refreshButton.disabled = false;
  }
}

function schedule(minutes) {
  if (pollTimer) {
    window.clearInterval(pollTimer);
  }
  const delay = Math.max(1, Number(minutes) || 60) * 60 * 1000;
  pollTimer = window.setInterval(() => load(false), delay);
}

function render(data) {
  const images = Array.isArray(data.images) ? data.images : [];
  statusNode.textContent = statusText(data, images);
  if (data.error) {
    errorNode.hidden = false;
    errorNode.textContent = data.error;
  } else {
    errorNode.hidden = true;
    errorNode.textContent = "";
  }
  renderChart(images);
  renderTable(images);
}

function statusText(data, images) {
  if (data.status === "loading") {
    return "Checking " + data.repository + " for the latest tags…";
  }
  const refreshed = formatTime(data.refreshedAt);
  const next = formatTime(data.nextRefreshAt);
  const scanner = scannerName(images);
  const parts = [
    data.repository,
    "last refreshed " + refreshed,
    "next refresh " + next
  ];
  if (scanner) {
    parts.push(scanner);
  }
  if (data.status === "stale") {
    parts.push("showing the previous report");
  }
  return parts.join(" · ");
}

function scannerName(images) {
  const names = images.map((image) => image.scanner).filter(Boolean);
  if (names.length === 0) {
    return "";
  }
  return names.every((name) => name === names[0]) ? names[0] : "multiple scanners";
}

function renderChart(images) {
  chartNode.replaceChildren();
  if (images.length === 0) {
    const empty = document.createElement("p");
    empty.className = "empty";
    empty.textContent = "No plain release tags are available yet.";
    chartNode.appendChild(empty);
    chartNode.setAttribute("aria-label", empty.textContent);
    return;
  }

  const totals = images.map((image) => countsOf(image).total);
  const scale = axisMax(totals);
  const summary = [];

  images.forEach((image) => {
    const counts = countsOf(image);
    const label = document.createElement("div");
    label.className = "tag-label";
    label.textContent = image.tag;

    const track = document.createElement("div");
    track.className = "track";
    const fill = document.createElement("div");
    fill.className = "fill";
    fill.style.width = scale === 0 ? "0%" : (counts.total / scale) * 100 + "%";
    if (counts.total === 0) {
      fill.style.minWidth = "0";
    }
    SEVERITIES.forEach((severity) => {
      const count = counts[severity.key];
      if (!count) {
        return;
      }
      const segment = document.createElement("span");
      segment.className = "seg";
      segment.style.width = (count / counts.total) * 100 + "%";
      segment.style.background = severity.color;
      segment.title = severity.label + ": " + count;
      fill.appendChild(segment);
    });
    track.appendChild(fill);

    const total = document.createElement("div");
    total.className = "total";
    total.textContent = image.error ? "n/a" : String(counts.total);
    if (image.error) {
      track.title = image.error;
    }

    chartNode.append(label, track, total);
    summary.push(image.tag + " " + counts.total);
  });

  const axis = document.createElement("div");
  axis.className = "axis";
  [0, 0.25, 0.5, 0.75, 1].forEach((fraction) => {
    const tick = document.createElement("span");
    tick.textContent = String(Math.round(scale * fraction));
    axis.appendChild(tick);
  });
  chartNode.appendChild(axis);
  chartNode.setAttribute(
    "aria-label",
    "Stacked bar chart of vulnerabilities. " + summary.join(", ") + "."
  );
}

function renderTable(images) {
  tableBody.replaceChildren();
  if (images.length === 0) {
    const row = document.createElement("tr");
    const cell = document.createElement("td");
    cell.colSpan = 10;
    cell.textContent = "Waiting for the first catalog refresh.";
    row.appendChild(cell);
    tableBody.appendChild(row);
    return;
  }

  images.forEach((image) => {
    const counts = countsOf(image);
    const row = document.createElement("tr");
    appendCell(row, image.tag, "tag");
    appendCell(row, formatTime(image.publishedAt));
    appendCell(row, image.error ? image.error : formatTime(image.scannedAt), image.error ? "row-error" : "");
    SEVERITIES.forEach((severity) => appendCell(row, String(counts[severity.key])));
    appendCell(row, String(counts.total));
    if (image.digest) {
      row.title = image.digest;
    }
    tableBody.appendChild(row);
  });
}

function appendCell(row, text, className) {
  const cell = document.createElement("td");
  cell.textContent = text;
  if (className) {
    cell.className = className;
  }
  row.appendChild(cell);
}

function countsOf(image) {
  const counts = image.counts || {};
  const value = { total: 0 };
  SEVERITIES.forEach((severity) => {
    const number = Math.max(0, Number(counts[severity.key]) || 0);
    value[severity.key] = number;
    value.total += number;
  });
  return value;
}

function axisMax(values) {
  const max = Math.max(1, ...values, 0);
  if (max <= 1) {
    return 1;
  }
  const power = Math.pow(10, Math.floor(Math.log10(max)));
  const normalized = max / power;
  const nice = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10;
  return nice * power;
}

function formatTime(value) {
  if (!value) {
    return "—";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(date);
}
