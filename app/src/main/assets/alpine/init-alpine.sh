export PATH=/bin:/sbin:/usr/bin:/usr/sbin:/usr/share/bin:/usr/share/sbin:/usr/local/bin:/usr/local/sbin:/system/bin:/system/xbin:$PREFIX/local/bin
export PS1="\[\e[38;5;46m\]\u\[\033[39m\]@localhost \[\033[39m\]\w \[\033[0m\]\\$ "
export HOME=/public
export TERM=xterm-256color

INSTALLING=false
FAILSAFE=false

# Parse internal flags
while [ $# -gt 0 ]; do
    case "$1" in
        --installing)
            INSTALLING=true
            shift
            ;;
        --failsafe)
            FAILSAFE=true
            shift
            ;;
        --)
            shift
            break
            ;;
        *)
            break
            ;;
    esac
done

# If a command was supplied, execute it and exit
# without it Executor will break
if [ "$INSTALLING" != true ] && [ $# -gt 0 ] && [ "${1#--}" = "$1" ]; then
    exec "$@"
fi

if [ ! -f /linkerconfig/ld.config.txt ]; then
    mkdir -p /linkerconfig
    touch /linkerconfig/ld.config.txt
fi


if [ "$INSTALLING" = true ]; then
    echo "Configuring timezone..."

    if [ -n "$ANDROID_TZ" ] && [ -f "/usr/share/zoneinfo/$ANDROID_TZ" ]; then
        ln -sf "/usr/share/zoneinfo/$ANDROID_TZ" /etc/localtime
        echo "$ANDROID_TZ" > /etc/timezone
        echo "Timezone set to: $ANDROID_TZ"
    else
        echo "Failed to detect timezone"
    fi

    mkdir -p "$PREFIX/.configured"

    if [ ! -f "$HOME/.bashrc" ]; then
       touch "$HOME/.bashrc" && chmod 644 "$HOME/.bashrc"
    fi

    echo "Installation completed."
    exit 0
fi



    echo "$$" > "$PREFIX/pid"

    # Create initrc if it doesn't exist
    # initrc runs in bash so we can use bash features
if [ ! -e "$PREFIX/alpine/initrc" ]; then
    cat <<'EOF' > "$PREFIX/alpine/initrc"
# Source rc files if they exist

if [ -f "/etc/profile" ]; then
    source "/etc/profile"
fi

# Environment setup
export PATH=$PATH:/bin:/sbin:/usr/bin:/usr/sbin:/usr/share/bin:/usr/share/sbin:/usr/local/bin:/usr/local/sbin

export HOME=/public
export TERM=xterm-256color
SHELL=/bin/bash
export PIP_BREAK_SYSTEM_PACKAGES=1
export PS1='\[\033[1;32m\]alpine\[\033[0m\]:\[\033[1;34m\]\w\[\033[0m\]\$ '

# Replicate behaviour of termux
alias clear='reset'

if [ -f "$HOME/.bashrc" ]; then
    source "$HOME/.bashrc"
fi

EOF
fi


chmod +x "$PREFIX/alpine/initrc"

# First-time terminal open script execution
if [ ! -f "$PREFIX/.first_boot_script_run" ]; then
    if [ -f "$PREFIX/setup-alpine.sh" ]; then
        /bin/sh "$PREFIX/setup-alpine.sh" 2>/dev/null || true
    elif [ -f /setup-alpine.sh ]; then
        /bin/sh /setup-alpine.sh 2>/dev/null || true
    fi
    touch "$PREFIX/.first_boot_script_run" 2>/dev/null || true
fi

# Shell Login System: Bash if installed, otherwise fallback to /bin/sh
if [ -x /bin/bash ]; then
    exec /bin/bash --rcfile /initrc -i
elif [ -x /usr/bin/bash ]; then
    exec /usr/bin/bash --rcfile /initrc -i
elif [ -x "$PREFIX/alpine/bin/bash" ]; then
    exec "$PREFIX/alpine/bin/bash" --rcfile /initrc -i
elif [ -x "$PREFIX/alpine/usr/bin/bash" ]; then
    exec "$PREFIX/alpine/usr/bin/bash" --rcfile /initrc -i
else
    exec /bin/sh -i
fi
