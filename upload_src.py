import paramiko
import os

c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('192.168.100.40', username='nestor', password='ian20jesus', timeout=10, look_for_keys=False, allow_agent=False)

sftp = c.open_sftp()

local_dir = r"C:\Users\Nestor\Workspace\Xuper\XuperPlugin\app\src\main\java\com\xuper\plugin"
remote_dir = "/home/nestor/Desktop/xuper/plugin/app/src/main/java/com/xuper/plugin"

for fname in ['XuperApiClient.kt', 'ConfigActivity.kt']:
    local_path = os.path.join(local_dir, fname)
    remote_path = os.path.join(remote_dir, fname)
    try:
        sftp.put(local_path, remote_path)
        print(f"Uploaded: {fname}")
    except Exception as e:
        print(f"Failed {fname}: {e}")

sftp.close()
c.close()
print("Done")
