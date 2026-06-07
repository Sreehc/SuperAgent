import os
import textwrap
import subprocess
import tempfile
from typing import Union

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
    limits: dict[str, Union[int, str]]


app = FastAPI(title="SuperAgent Sandbox Runner", version="0.1.0")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/internal/sandbox/execute", response_model=SandboxResponse)
def execute(payload: SandboxRequest) -> SandboxResponse:
    python_bin = os.environ.get("SANDBOX_PYTHON_BIN", "python3")
    max_code_length = int(os.environ.get("SANDBOX_MAX_CODE_LENGTH", "20000"))
    max_memory_bytes = int(os.environ.get("SANDBOX_MAX_MEMORY_BYTES", str(256 * 1024 * 1024)))
    max_file_bytes = int(os.environ.get("SANDBOX_MAX_FILE_BYTES", str(1024 * 1024)))
    if len(payload.code) > max_code_length:
        raise HTTPException(status_code=400, detail={"status": "failed", "stderr": "code_too_large"})
    bootstrap = textwrap.dedent(
        f"""
        import builtins
        import os
        import socket

        WORKSPACE_ROOT = os.path.realpath(os.getcwd())
        _original_open = builtins.open

        def _deny_network(*args, **kwargs):
            raise PermissionError("network_disabled")

        class _BlockedSocket(socket.socket):
            def __init__(self, *args, **kwargs):
                raise PermissionError("network_disabled")

        def _guarded_open(file, mode="r", *args, **kwargs):
            target = os.path.realpath(file if os.path.isabs(file) else os.path.join(WORKSPACE_ROOT, file))
            write_mode = any(flag in mode for flag in ("w", "a", "x", "+"))
            if write_mode and not (target == WORKSPACE_ROOT or target.startswith(WORKSPACE_ROOT + os.sep)):
                raise PermissionError("write_outside_sandbox")
            return _original_open(file, mode, *args, **kwargs)

        builtins.open = _guarded_open
        socket.socket = _BlockedSocket
        socket.create_connection = _deny_network
        socket.getaddrinfo = _deny_network

        try:
            import resource
            resource.setrlimit(resource.RLIMIT_CPU, ({payload.timeout_seconds}, {payload.timeout_seconds + 1}))
            resource.setrlimit(resource.RLIMIT_AS, ({max_memory_bytes}, {max_memory_bytes}))
            resource.setrlimit(resource.RLIMIT_FSIZE, ({max_file_bytes}, {max_file_bytes}))
        except Exception:
            pass
        """
    )
    wrapped_code = bootstrap + "\n" + payload.code
    with tempfile.TemporaryDirectory(prefix="superagent-sandbox-") as temp_dir:
        try:
            completed = subprocess.run(
                [python_bin, "-I", "-c", wrapped_code],
                cwd=temp_dir,
                capture_output=True,
                text=True,
                timeout=payload.timeout_seconds,
                env={
                    "PATH": os.environ.get("PATH", ""),
                    "PYTHONPATH": "",
                    "PYTHONNOUSERSITE": "1",
                    "HOME": temp_dir,
                    "TMPDIR": temp_dir,
                },
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
        limits={
            "network": "disabled",
            "timeoutSeconds": payload.timeout_seconds,
            "maxOutputBytes": payload.max_output_bytes,
            "maxMemoryBytes": max_memory_bytes,
            "maxFileBytes": max_file_bytes,
        },
    )


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app:app", host="0.0.0.0", port=int(os.environ.get("SANDBOX_PORT", "18082")))
