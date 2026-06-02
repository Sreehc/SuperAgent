import os
import subprocess
import tempfile
from typing import Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field


class SandboxRequest(BaseModel):
    code: str = Field(min_length=1)
    timeout_seconds: int = Field(default=5, ge=1, le=30)
    max_output_bytes: int = Field(default=32768, ge=1024, le=262144)


class SandboxResponse(BaseModel):
    status: str
    stdout: str
    stderr: str
    exit_code: int


app = FastAPI(title="SuperAgent Sandbox Runner", version="0.1.0")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/internal/sandbox/execute", response_model=SandboxResponse)
def execute(payload: SandboxRequest) -> SandboxResponse:
    python_bin = os.environ.get("SANDBOX_PYTHON_BIN", "python3")
    max_code_length = int(os.environ.get("SANDBOX_MAX_CODE_LENGTH", "20000"))
    if len(payload.code) > max_code_length:
        raise HTTPException(status_code=400, detail={"status": "failed", "stderr": "code_too_large"})
    with tempfile.TemporaryDirectory(prefix="superagent-sandbox-") as temp_dir:
        try:
            completed = subprocess.run(
                [python_bin, "-I", "-c", payload.code],
                cwd=temp_dir,
                capture_output=True,
                text=True,
                timeout=payload.timeout_seconds,
                env={"PYTHONPATH": "", "PYTHONNOUSERSITE": "1"},
            )
        except subprocess.TimeoutExpired as exc:
            raise HTTPException(status_code=408, detail={"status": "timeout", "stdout": exc.stdout or "", "stderr": exc.stderr or ""})
        except Exception as exc:  # pragma: no cover
            raise HTTPException(status_code=500, detail=str(exc))

    stdout = completed.stdout[: payload.max_output_bytes]
    stderr = completed.stderr[: payload.max_output_bytes]

    return SandboxResponse(
        status="success" if completed.returncode == 0 else "failed",
        stdout=stdout,
        stderr=stderr,
        exit_code=completed.returncode,
    )


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app:app", host="0.0.0.0", port=int(os.environ.get("SANDBOX_PORT", "18082")))
