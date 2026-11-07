-- Per-install daily usage counter used by the agent-chat Edge Function for rate limiting.
-- Only the service role (inside the Edge Function) may touch it. RLS is on and no policies exist,
-- so the public anon key cannot read or write it.
create table if not exists public.agent_usage (
  install_id text not null,
  day date not null default (now() at time zone 'utc')::date,
  requests integer not null default 0,
  prompt_tokens bigint not null default 0,
  output_tokens bigint not null default 0,
  updated_at timestamptz not null default now(),
  primary key (install_id, day)
);

alter table public.agent_usage enable row level security;

-- Atomically increments the counter and returns the new request count for today.
create or replace function public.agent_bump_usage(p_install_id text)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_count integer;
begin
  insert into public.agent_usage (install_id, day, requests)
  values (p_install_id, (now() at time zone 'utc')::date, 1)
  on conflict (install_id, day)
  do update set requests = public.agent_usage.requests + 1, updated_at = now()
  returning requests into v_count;
  return v_count;
end;
$$;

revoke all on function public.agent_bump_usage(text) from public, anon, authenticated;
grant execute on function public.agent_bump_usage(text) to service_role;
